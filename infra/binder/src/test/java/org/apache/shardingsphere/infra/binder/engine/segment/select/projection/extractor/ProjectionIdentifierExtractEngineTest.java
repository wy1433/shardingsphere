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

package org.apache.shardingsphere.infra.binder.engine.segment.select.projection.extractor;

import org.apache.shardingsphere.infra.binder.context.segment.select.projection.extractor.ProjectionIdentifierExtractEngine;
import org.apache.shardingsphere.infra.database.core.metadata.database.enums.QuoteCharacter;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.expr.subquery.SubquerySegment;
import org.apache.shardingsphere.sql.parser.statement.core.segment.dml.item.SubqueryProjectionSegment;
import org.apache.shardingsphere.sql.parser.statement.core.value.identifier.IdentifierValue;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

class ProjectionIdentifierExtractEngineTest {
    
    @Test
    void assertGetIdentifierValue() {
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "PostgreSQL")).getIdentifierValue(new IdentifierValue("Data", QuoteCharacter.QUOTE)),
                is("Data"));
        IdentifierValue identifierValue = new IdentifierValue("Data", QuoteCharacter.NONE);
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "PostgreSQL")).getIdentifierValue(identifierValue), is("data"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "openGauss")).getIdentifierValue(identifierValue), is("data"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "Oracle")).getIdentifierValue(identifierValue), is("DATA"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "MySQL")).getIdentifierValue(identifierValue), is("Data"));
    }
    
    @Test
    void assertGetColumnNameFromFunction() {
        String functionName = "Function";
        String functionExpression = "FunctionExpression";
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "PostgreSQL")).getColumnNameFromFunction(functionName, functionExpression), is("function"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "openGauss")).getColumnNameFromFunction(functionName, functionExpression), is("function"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "Oracle")).getColumnNameFromFunction(functionName, functionExpression),
                is("FUNCTIONEXPRESSION"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "MySQL")).getColumnNameFromFunction(functionName, functionExpression),
                is("FunctionExpression"));
    }
    
    @Test
    void assertGetColumnNameFromExpression() {
        String expression = "expression";
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "PostgreSQL")).getColumnNameFromExpression(expression), is("?column?"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "openGauss")).getColumnNameFromExpression(expression), is("?column?"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "Oracle")).getColumnNameFromExpression(expression), is("EXPRESSION"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "MySQL")).getColumnNameFromExpression(expression), is("expression"));
    }
    
    @Test
    void assertGetColumnNameFromSubquery() {
        SubqueryProjectionSegment projectionSegment = new SubqueryProjectionSegment(mock(SubquerySegment.class), "text");
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "PostgreSQL")).getColumnNameFromSubquery(projectionSegment), is("text"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "openGauss")).getColumnNameFromSubquery(projectionSegment), is("text"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "Oracle")).getColumnNameFromSubquery(projectionSegment), is("TEXT"));
        assertThat(new ProjectionIdentifierExtractEngine(TypedSPILoader.getService(DatabaseType.class, "MySQL")).getColumnNameFromSubquery(projectionSegment), is("text"));
    }
}
