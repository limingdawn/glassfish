/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.admin.cli.cluster;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;
import javax.xml.bind.*;

import org.jvnet.hk2.annotations.*;
import org.jvnet.hk2.component.*;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;

import com.sun.enterprise.admin.cli.*;
import com.sun.enterprise.admin.cli.remote.RemoteCommand;
import com.sun.enterprise.util.cluster.SyncRequest;
import com.sun.enterprise.util.io.FileUtils;
import com.sun.enterprise.universal.i18n.LocalStringsImpl;

/**
 * Synchronize a local server instance.
 */
@Service(name = "_synchronize-instance")
@Scoped(PerLookup.class)
public class SynchronizeInstanceCommand extends LocalInstanceCommand {

    @Param(name = "instance_name", primary = true, optional = true)
    private String instanceName0;

    @Param(name = "syncarchive", optional = true)
    private boolean syncArchive;

    @Param(name = "syncallapps", optional = true)
    private boolean syncAllApps;

    private RemoteCommand syncCmd;

    private static enum SyncLevel { TOP, DIRECTORY, RECURSIVE };

    private static final LocalStringsImpl strings =
            new LocalStringsImpl(SynchronizeInstanceCommand.class);

    @Override
    protected void validate()
                        throws CommandException, CommandValidationException {
        instanceName = instanceName0;
        super.validate();
    }

    /**
     */
    @Override
    protected int executeCommand()
            throws CommandException, CommandValidationException {
        if (synchronizeInstance())
            return SUCCESS;
        else
            return ERROR;
    }

    /**
     * Synchronize this server instance.  Return true if
     * server is synchronized.
     */
    protected boolean synchronizeInstance() throws CommandException {

        File dasProperties = getServerDirs().getDasPropertiesFile();
        logger.printDebugMessage("das.properties: " + dasProperties);

        if (!dasProperties.exists()) {
            logger.printMessage(
                strings.get("Sync.noDASConfigured", dasProperties.toString()));
            return false;
        }

        setDasDefaults(dasProperties);

        /*
         * Create the remote command object that we'll reuse for each request.
         */
        syncCmd = new RemoteCommand("_synchronize-files", programOpts, env);
        syncCmd.setFileOutputDirectory(instanceDir);

        /*
         * First, synchronize the config directory.
         */
        File domainXml =
            new File(new File(instanceDir, "config"), "domain.xml");
        long dtime = domainXml.lastModified();

        SyncRequest sr = getModTimes("config", SyncLevel.DIRECTORY);
        synchronizeFiles(sr);

        /*
         * Was domain.xml updated?
         * If not, we're all done.
         */
        if (domainXml.lastModified() == dtime) {
            logger.printDetailMessage(strings.get("Sync.alreadySynced"));
            return true;
        }

        /*
         * Now synchronize the applications.
         */
        sr = getModTimes("applications", SyncLevel.DIRECTORY);
        synchronizeFiles(sr);

        /*
         * Did we get any archive files?  If so,
         * have to unzip them in the applications
         * directory.
         */
        File appsDir = new File(instanceDir, "applications");
        File archiveDir = new File(appsDir, "__internal");
        for (File adir : FileUtils.listFiles(archiveDir)) {
            File[] af = FileUtils.listFiles(adir);
            if (af.length != 1) {
System.out.println("IGNORING " + adir + ", # files " + af.length);
                continue;
            }
            File archive = af[0];
            File appDir = new File(appsDir, adir.getName());
System.out.println("UNZIP " + archive + " TO " + appDir);
            try {
                expand(appDir, archive);
            } catch (Exception ex) { }
        }

        FileUtils.whack(archiveDir);

        /*
         * Next, the libraries.
         * We assume there's usually very few files in the
         * "lib" directory so we check them all individually.
         */
        sr = getModTimes("lib", SyncLevel.RECURSIVE);
        synchronizeFiles(sr);

        /*
         * Next, the docroot.
         * The docroot could be full of files, so we only check
         * one level.
         */
        sr = getModTimes("docroot", SyncLevel.DIRECTORY);
        synchronizeFiles(sr);

        /*
         * Check any subdirectories of the instance directory.
         * We only expect one - the config-specific directory,
         * but since we don't have an easy way of knowing the
         * name of that directory, we include them all.  The
         * DAS will tell us to remove anything that shouldn't
         * be there.
         */
        sr = new SyncRequest();
        sr.instance = instanceName;
        sr.dir = "config-specific";
        File configDir = new File(instanceDir, "config");
        for (File f : configDir.listFiles()) {
            if (!f.isDirectory())
                continue;
            getFileModTimes(configDir, f, sr, SyncLevel.DIRECTORY);
        }
        synchronizeFiles(sr);

        return true;
    }

    /**
     * Return a SyncRequest with the mod times for all the
     * files in the specified directory.
     */
    private SyncRequest getModTimes(String dir, SyncLevel level) {
        SyncRequest sr = new SyncRequest();
        sr.instance = instanceName;
        sr.dir = dir;
        File fdir = new File(instanceDir, dir);
        if (!fdir.exists())
            return sr;
        getFileModTimes(fdir, fdir, sr, level);
        return sr;
    }

    /**
     * Get the mod times for the entries in dir and add them to the
     * SyncRequest, using names relative to baseDir.  If level is
     * RECURSIVE, check subdirectories and only include times for files,
     * not directories.
     */
    private void getFileModTimes(File dir, File baseDir, SyncRequest sr,
                                    SyncLevel level) {
        if (level == SyncLevel.TOP) {
            long time = dir.lastModified();
            SyncRequest.ModTime mt = new SyncRequest.ModTime(".", time);
            sr.files.add(mt);
            return;
        }
        for (String file : dir.list()) {
            File f = new File(dir, file);
            long time = f.lastModified();
            if (time == 0)
                continue;
            if (f.isDirectory() && level == SyncLevel.RECURSIVE)
                getFileModTimes(f, baseDir, sr, level);
            else {
                String name = baseDir.toURI().relativize(f.toURI()).getPath();
                // if name is a directory, it will end with "/"
                if (name.endsWith("/"))
                    name = name.substring(0, name.length() - 1);
                SyncRequest.ModTime mt = new SyncRequest.ModTime(name, time);
                sr.files.add(mt);
                logger.printDebugMessage(f + ": mod time " + mt.time);
            }
        }
    }

    /**
     * Ask the server to synchronize the files in the SyncRequest.
     */
    private void synchronizeFiles(SyncRequest sr) throws CommandException {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("mt.", ".xml");
            tempFile.deleteOnExit();

            JAXBContext context = JAXBContext.newInstance(SyncRequest.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty("jaxb.formatted.output", Boolean.TRUE);
            marshaller.marshal(sr, tempFile);
            if (logger.isLoggable(Level.FINER))
                marshaller.marshal(sr, System.out);

            File syncdir = new File(instanceDir, sr.dir);
            logger.printDebugMessage("Sync directory: " + syncdir);
            // _synchronize-files takes a single operand of type File
            syncCmd.execute("_synchronize-files",
                "--syncarchive", Boolean.toString(syncArchive),
                "--syncallapps", Boolean.toString(syncAllApps),
                tempFile.getPath());

            // the returned files are automatically saved by the command
        } catch (IOException ioex) {
            throw new CommandException(strings.get("sync.failed", sr.dir));
        } catch (JAXBException jbex) {
            throw new CommandException(strings.get("sync.failed", sr.dir));
        } catch (CommandException cex) {
            throw new CommandException(strings.get("sync.failed", sr.dir));
        } finally {
            // remove tempFile
            if (tempFile != null)
                tempFile.delete();
        }
    }

    /**
     * Expand the archive to the specified directory.
     * XXX - this doesn't handle all the cases required for a Java EE app,
     * but it's good enough for now for some performance testing
     */
    private static void expand(File dir, File archive) throws Exception {
        dir.mkdir();
        long modtime = archive.lastModified();
        ZipFile zf = new ZipFile(archive);
        Enumeration<? extends ZipEntry> e = zf.entries();
        while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            File entry = new File(dir, ze.getName());
            if (ze.isDirectory()) {
                entry.mkdir();
            } else {
                FileUtils.copy(zf.getInputStream(ze),
                                new FileOutputStream(entry), 0);
            }
        }
        dir.setLastModified(modtime);
    }

    /**
     * Set the programOptions based on the das.properties file.
     */
    private void setDasDefaults(File propfile) throws CommandException {
        Properties dasprops = new Properties();
        FileInputStream fis = null;
        try {
            // read properties and set them in programOptions
            // properties are:
            // agent.das.port
            // agent.das.host
            // agent.das.isSecure
            // agent.das.user           XXX - not in v2?
            fis = new FileInputStream(propfile);
            dasprops.load(fis);
            String p;
            p = dasprops.getProperty("agent.das.host");
            if (p != null)
                programOpts.setHost(p);
            p = dasprops.getProperty("agent.das.port");
            if (p != null)
                programOpts.setPort(Integer.parseInt(p));
            p = dasprops.getProperty("agent.das.isSecure");
            if (p != null)
                programOpts.setSecure(Boolean.parseBoolean(p));
            p = dasprops.getProperty("agent.das.user");
            if (p != null)
                programOpts.setUser(p);
            // XXX - what about the DAS admin password?
        } catch (IOException ioex) {
            throw new CommandException(
                        strings.get("Instance.cantReadDasProperties",
                                    propfile.getPath()));
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException cex) {
                    // ignore it
                }
            }
        }
    }
}
