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
package org.apache.ivy.util;

import java.io.*;
import java.util.Properties;

/**
 * A simple Properties extension easing the loading and saving of data
 */
public class PropertiesFile extends Properties {
    private File file;

    private String header;

    public PropertiesFile(File file, String header) {
        this.file = file;
        this.header = header;
        if (file.exists()) {
            InputStream fis = null;
            try {
                fis = FileUtil.newInputStream(file);
                load(fis);
            } catch (Exception ex) {
                Message.warn("exception occurred while reading properties file " + file, ex);
            }
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException e) {
                // ignored
            }
        }
    }

    public void save() {
        OutputStream fos = null;
        try {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            fos = FileUtil.newOutputStream(file);
            store(fos, header);
        } catch (Exception ex) {
            Message.warn("exception occurred while writing properties file " + file, ex);
        }
        try {
            if (fos != null) {
                fos.close();
            }
        } catch (IOException e) {
            // ignored
        }
    }

}
