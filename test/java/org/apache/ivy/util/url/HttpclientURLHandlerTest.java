/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.util.url;

import java.io.File;
import java.net.URL;

import junit.framework.TestCase;

import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.url.URLHandler.URLInfo;

/**
 * Test HttpClientHandler
 */
public class HttpclientURLHandlerTest extends TestCase {
    // remote.test
    private File testDir;

    private HttpClientHandler handler;

    protected void setUp() throws Exception {
        testDir = FileUtil.newFile("build/HttpclientURLHandlerTest");
        testDir.mkdirs();

        handler = new HttpClientHandler();
    }

    protected void tearDown() throws Exception {
        FileUtil.forceDelete(testDir);
    }

    public void testIsReachable() throws Exception {
        assertTrue(handler.isReachable(new URL("http://www.google.fr/")));
        assertFalse(handler.isReachable(new URL("http://www.google.fr/unknownpage.html")));
    }

    public void testGetURLInfo() throws Exception {
        // IVY-390
        URLHandler handler = new HttpClientHandler();
        URLInfo info = handler
                .getURLInfo(new URL(
                        "http://repo1.maven.org/maven2/commons-lang/commons-lang/[1.0,3.0[/commons-lang-[1.0,3.0[.pom"));

        assertEquals(URLHandler.UNAVAILABLE, info);
    }

    public void testContentEncoding() throws Exception {
        assertDownloadOK(new URL("http://carsten.codimi.de/gzip.yaws/daniels.html"), FileUtil.newFile(
                testDir, "gzip.txt"));
        assertDownloadOK(new URL(
                "http://carsten.codimi.de/gzip.yaws/daniels.html?deflate=on&zlib=on"), FileUtil.newFile(
                testDir, "deflate-zlib.txt"));
        assertDownloadOK(new URL("http://carsten.codimi.de/gzip.yaws/daniels.html?deflate=on"),
            FileUtil.newFile(testDir, "deflate.txt"));
        assertDownloadOK(new URL("http://carsten.codimi.de/gzip.yaws/a5.ps"), FileUtil.newFile(testDir,
                "a5-gzip.ps"));
        assertDownloadOK(new URL("http://carsten.codimi.de/gzip.yaws/a5.ps?deflate=on"), FileUtil.newFile(
                testDir, "a5-deflate.ps"));
        assertDownloadOK(new URL("http://carsten.codimi.de/gzip.yaws/nh80.pdf"), FileUtil.newFile(testDir,
                "nh80-gzip.pdf"));
        assertDownloadOK(new URL("http://carsten.codimi.de/gzip.yaws/nh80.pdf?deflate=on"),
            FileUtil.newFile(testDir, "nh80-deflate.pdf"));
    }

    private void assertDownloadOK(URL url, File file) throws Exception {
        handler.download(url, file, null);
        assertTrue(file.exists());
    }
}
