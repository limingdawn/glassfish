/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

package com.sun.enterprise.v3.services.impl;

/**
 * Set of Grizzly network utilities
 * 
 * @author Alexey Stashok
 */
public class GrizzlyUtils {
    /**
     * Reads bytes to the <code>WorkerThread</code> associated <code>ByteBuffer</code>s.
     * Could be used to read both raw and secured data.
     * 
     * @param key <code>SelectionKey</code>
     * @param timeout read timeout
     * @return number of read bytes
     * @throws java.io.IOException
     */
//    public static int readToWorkerThreadBuffers(SelectionKey key, int timeout) throws IOException {
//        Object attachment = key.attachment();
//        SSLEngine sslEngine = null;
//        if (attachment instanceof ThreadAttachment) {
//            sslEngine = ((ThreadAttachment) attachment).getSSLEngine();
//        }
//
//        WorkerThread thread = (WorkerThread) Thread.currentThread();
//
//        if (sslEngine == null) {
//            return Utils.readWithTemporarySelector(key.channel(),
//                    thread.getByteBuffer(), timeout).bytesRead;
//        } else {
//            // if ssl - try to unwrap secured buffer first
//            ByteBuffer byteBuffer = thread.getByteBuffer();
//            ByteBuffer securedBuffer = thread.getInputBB();
//
//            if (securedBuffer.position() > 0) {
//                int initialPosition = byteBuffer.position();
//                byteBuffer =
//                        SSLUtils.unwrapAll(byteBuffer, securedBuffer, sslEngine);
//                int producedBytes = byteBuffer.position() - initialPosition;
//                if (producedBytes > 0) {
//                    return producedBytes;
//                }
//            }
//
//            // if no bytes were unwrapped - read more
//            return SSLUtils.doSecureRead(key.channel(), sslEngine, byteBuffer,
//                    securedBuffer).bytesRead;
//        }
//    }
}
