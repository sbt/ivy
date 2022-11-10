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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.apache.ivy.core.IvyPatternHelper;

public class IvyPatternHelperTest extends TestCase {
    public void testSubstitute() {
        String pattern = "[organisation]/[module]/build/archives/[type]s/[artifact]-[revision].[ext]";
        assertEquals("apache/Test/build/archives/jars/test-1.0.jar", IvyPatternHelper.substitute(
            pattern, "apache", "Test", "1.0", "test", "jar", "jar"));
    }

    public void testCyclicSubstitute() {
        String pattern = "${var}";
        Map variables = new HashMap();
        variables.put("var", "${othervar}");
        variables.put("othervar", "${var}");
        try {
            IvyPatternHelper.substituteVariables(pattern, variables);
            fail("cyclic var should raise an exception");
        } catch (Exception ex) {
            // ok
        } catch (Error er) {
            fail("cyclic var shouldn't raise an error: " + er);
        }
    }

    public void testOptionalSubstitute() {
        Map tokens = new HashMap();
        tokens.put("token", "");
        tokens.put("othertoken", "myval");
        assertEquals("test-myval", IvyPatternHelper.substituteTokens(
            "test(-[token])(-[othertoken])", tokens));
        tokens.put("token", "val");
        assertEquals("test-val-myval", IvyPatternHelper.substituteTokens(
            "test(-[token])(-[othertoken])", tokens));
    }

    public void testOrganization() {
        String pattern = "[organization]/[module]/build/archives/[type]s/[artifact]-[revision].[ext]";
        assertEquals("apache/Test/build/archives/jars/test-1.0.jar", IvyPatternHelper.substitute(
            pattern, "apache", "Test", "1.0", "test", "jar", "jar"));
    }
    
    public void testSpecialCharsInsidePattern() {
        String pattern = "[organization]/[module]/build/archives (x86)/[type]s/[artifact]-[revision].[ext]";
        assertEquals("apache/Test/build/archives (x86)/jars/test-1.0.jar", IvyPatternHelper.substitute(
            pattern, "apache", "Test", "1.0", "test", "jar", "jar"));
    }

    public void testTokenRoot() {
        String pattern = "lib/[type]/[artifact].[ext]";
        assertEquals("lib/", IvyPatternHelper.getTokenRoot(pattern));
    }

    public void testTokenRootWithOptionalFirstToken() {
        String pattern = "lib/([type]/)[artifact].[ext]";
        assertEquals("lib/", IvyPatternHelper.getTokenRoot(pattern));
    }

    public void testRejectsPathTraversalInOrganisation() {
        try {
            String pattern = "[organisation]/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "../org", "module", "revision", "artifact", "type", "ext", "conf");
        } catch (IllegalArgumentException ex) {
            // success
        }
    }

    public void testRejectsPathTraversalInOrganization() {
        try {
            String pattern = "[organization]/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "../org", "module", "revision", "artifact", "type", "ext", "conf");
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex) {
            // success
        }
    }

    public void testRejectsPathTraversalInModule() {
        try {
            String pattern = "[module]/build/archives (x86)/[type]s/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "org", "..\\module", "revision", "artifact", "type", "ext", "conf");
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

    public void testRejectsPathTraversalInRevision() {
        try {
            String pattern = "[type]s/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "org", "module", "revision/..", "artifact", "type", "ext", "conf");
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

    public void testRejectsPathTraversalInArtifact() {
        try {
            String pattern = "[type]s/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "org", "module", "revision", "artifact\\..", "type", "ext", "conf");
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

    public void testRejectsPathTraversalInType() {
        try {
            String pattern = "[type]s/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "org", "module", "revision", "artifact", "ty/../pe", "ext", "conf");
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

    public void testRejectsPathTraversalInExt() {
        try {
            String pattern = "[type]s/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "org", "module", "revision", "artifact", "type", "ex//..//t", "conf");
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

    public void testRejectsPathTraversalInConf() {
        try {
            String pattern = "[conf]/[artifact]-[revision].[ext]";
            IvyPatternHelper.substitute(pattern, "org", "module", "revision", "artifact", "type", "ext", "co\\..\\nf");
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex) {
            // success
        }
    }

    public void testRejectsPathTraversalInModuleAttributes() {
        try {
            String pattern = "[foo]/[artifact]-[revision].[ext]";
            Map<String, String> a = new HashMap<String, String>() {{
                put("foo", "..");
            }};
            IvyPatternHelper.substitute(pattern, "org", "module", "revision", "artifact", "type", "ext", "conf",
                    a, Collections.emptyMap());
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

    public void testRejectsPathTraversalInArtifactAttributes() {
        try {
            String pattern = "[foo]/[artifact]-[revision].[ext]";
            Map<String, String> a = new HashMap<String, String>() {{
                put("foo", "a/../b");
            }};
            IvyPatternHelper.substitute(pattern, "org", "module", "revision", "artifact", "type", "ext", "conf",
                    Collections.emptyMap(), a);
            fail("A IllegalArgumentException should have been thrown.");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

    public void testIgnoresPathTraversalInCoordinatesNotUsedInPatern() {
        String pattern = "abc";
        Map<String, String> a = new HashMap<String, String>() {{
            put("foo", "a/../b");
        }};
        assertEquals("abc",
            IvyPatternHelper.substitute(pattern, "../org", "../module", "../revision", "../artifact", "../type", "../ext", "../conf",
                a, a)
        );
    }

    public void testRejectsPathTraversalWithoutExplicitDoubleDot() {
        try {
            String pattern = "root/[conf]/[artifact]-[revision].[ext]";
            // forms revision/../ext after substitution
            IvyPatternHelper.substitute(pattern, "org", "module", "revision/", "artifact", "type", "./ext", "conf");
        } catch (IllegalArgumentException ex){
            // success
        }
    }

}
