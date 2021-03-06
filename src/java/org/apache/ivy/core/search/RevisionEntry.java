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
package org.apache.ivy.core.search;

import org.apache.ivy.plugins.resolver.DependencyResolver;

public class RevisionEntry {
    private ModuleEntry moduleEntry;

    private String revision;

    public RevisionEntry(ModuleEntry mod, String name) {
        moduleEntry = mod;
        revision = name;
    }

    public ModuleEntry getModuleEntry() {
        return moduleEntry;
    }

    public String getRevision() {
        return revision;
    }

    public String getModule() {
        return moduleEntry.getModule();
    }

    public String getOrganisation() {
        return moduleEntry.getOrganisation();
    }

    public OrganisationEntry getOrganisationEntry() {
        return moduleEntry.getOrganisationEntry();
    }

    public DependencyResolver getResolver() {
        return moduleEntry.getResolver();
    }

    public String toString() {
        return moduleEntry + ";" + revision;
    }
}
