/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.mode.node.path.type.metadata.database;

import org.apache.shardingsphere.mode.node.path.engine.generator.NodePathGenerator;
import org.apache.shardingsphere.mode.node.path.engine.searcher.NodePathSearcher;
import org.apache.shardingsphere.mode.node.path.type.metadata.schema.ViewMetadataNodePath;
import org.apache.shardingsphere.mode.node.path.type.version.VersionNodePath;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewMetadataNodePathTest {
    
    @Test
    void assertToPath() {
        assertThat(NodePathGenerator.toPath(new ViewMetadataNodePath("foo_db", null, null), false), is("/metadata/foo_db/schemas"));
        assertThat(NodePathGenerator.toPath(new ViewMetadataNodePath("foo_db", "foo_schema", null), false), is("/metadata/foo_db/schemas/foo_schema/views"));
        assertThat(NodePathGenerator.toPath(new ViewMetadataNodePath("foo_db", "foo_schema", null), true), is("/metadata/foo_db/schemas/foo_schema"));
        assertThat(NodePathGenerator.toPath(new ViewMetadataNodePath("foo_db", "foo_schema", "foo_view"), false), is("/metadata/foo_db/schemas/foo_schema/views/foo_view"));
    }
    
    @Test
    void assertToVersionPath() {
        VersionNodePath versionNodePath = new VersionNodePath(new ViewMetadataNodePath("foo_db", "foo_schema", "foo_view"));
        assertThat(versionNodePath.getActiveVersionPath(), is("/metadata/foo_db/schemas/foo_schema/views/foo_view/active_version"));
        assertThat(versionNodePath.getVersionsPath(), is("/metadata/foo_db/schemas/foo_schema/views/foo_view/versions"));
        assertThat(versionNodePath.getVersionPath(0), is("/metadata/foo_db/schemas/foo_schema/views/foo_view/versions/0"));
    }
    
    @Test
    void assertCreateViewSearchCriteria() {
        assertThat(NodePathSearcher.get("/metadata/foo_db/schemas/foo_schema/views/foo_view", ViewMetadataNodePath.createViewSearchCriteria("foo_db", "foo_schema")), is("foo_view"));
        assertFalse(NodePathSearcher.find("/xxx/foo_db/schemas/foo_schema/views/foo_view", ViewMetadataNodePath.createViewSearchCriteria("foo_db", "foo_schema")).isPresent());
        assertTrue(NodePathSearcher.isMatchedPath("/metadata/foo_db/schemas/foo_schema/views/foo_view", ViewMetadataNodePath.createViewSearchCriteria("foo_db", "foo_schema")));
        assertTrue(NodePathSearcher.isMatchedPath("/metadata/foo_db/schemas/foo_schema/views/foo_view/versions/0", ViewMetadataNodePath.createViewSearchCriteria("foo_db", "foo_schema")));
    }
}
