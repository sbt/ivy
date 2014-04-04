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
package org.apache.ivy.plugins.resolver.util;

import java.io.File;
import java.util.Arrays;

import junit.framework.TestCase;

import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.util.FileUtil;

public class ResolverHelperTest extends TestCase {

    public void testListTokenValuesForIvy1238() {
        FileRepository rep = new FileRepository(FileUtil.newFile(".").getAbsoluteFile());
        String[] revisions = ResolverHelper.listTokenValues(rep,
            "test/repositories/IVY-1238/ivy-org/modA/v[revision]/ivy.xml", "revision");

        assertNotNull(revisions);
        assertEquals(2, revisions.length);

        Arrays.sort(revisions);
        assertEquals("1.0", revisions[0]);
        assertEquals("2.0", revisions[1]);
    }

}
