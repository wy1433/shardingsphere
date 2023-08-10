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

package org.apache.shardingsphere.infra.datasource.pool.creator;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.shardingsphere.infra.database.core.GlobalDataSourceRegistry;
import org.apache.shardingsphere.infra.database.core.connector.url.JdbcUrl;
import org.apache.shardingsphere.infra.database.core.connector.url.StandardJdbcUrlParser;
import org.apache.shardingsphere.infra.database.core.connector.url.UnrecognizedDatabaseURLException;
import org.apache.shardingsphere.infra.database.core.metadata.database.DialectDatabaseMetaData;
import org.apache.shardingsphere.infra.database.core.type.DatabaseType;
import org.apache.shardingsphere.infra.database.core.type.DatabaseTypeFactory;
import org.apache.shardingsphere.infra.database.core.type.DatabaseTypeRegistry;
import org.apache.shardingsphere.infra.datasource.pool.destroyer.DataSourcePoolDestroyer;
import org.apache.shardingsphere.infra.datasource.pool.metadata.DataSourcePoolMetaData;
import org.apache.shardingsphere.infra.datasource.pool.metadata.DataSourcePoolMetaDataReflection;
import org.apache.shardingsphere.infra.datasource.pool.props.DataSourceProperties;
import org.apache.shardingsphere.infra.datasource.pool.props.custom.CustomDataSourceProperties;
import org.apache.shardingsphere.infra.datasource.storage.StorageNodeProperties;
import org.apache.shardingsphere.infra.datasource.storage.StorageResource;
import org.apache.shardingsphere.infra.datasource.storage.StorageResourceWithProperties;
import org.apache.shardingsphere.infra.datasource.storage.StorageUnitNodeMapper;
import org.apache.shardingsphere.infra.spi.type.typed.TypedSPILoader;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Data source pool creator.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSourcePoolCreator {
    
    /**
     * Create storage resource.
     *
     * @param dataSourcePropsMap data source properties map
     * @return created storage resource
     */
    public static StorageResource createStorageResource(final Map<String, DataSourceProperties> dataSourcePropsMap) {
        return createStorageResource(dataSourcePropsMap, true);
    }
    
    /**
     * Create storage resource.
     *
     * @param dataSourcePropsMap data source properties map
     * @param cacheEnabled cache enabled
     * @return created storage resource
     */
    public static StorageResource createStorageResource(final Map<String, DataSourceProperties> dataSourcePropsMap, final boolean cacheEnabled) {
        Map<String, DataSource> storageNodes = new LinkedHashMap<>();
        Map<String, StorageUnitNodeMapper> storageUnitNodeMappers = new LinkedHashMap<>();
        for (Entry<String, DataSourceProperties> entry : dataSourcePropsMap.entrySet()) {
            StorageNodeProperties storageNodeProps = getStorageNodeProperties(entry.getKey(), entry.getValue());
            if (storageNodes.containsKey(storageNodeProps.getName())) {
                appendStorageUnitNodeMapper(storageUnitNodeMappers, storageNodeProps, entry.getKey(), entry.getValue());
                continue;
            }
            DataSource dataSource;
            try {
                dataSource = create(entry.getKey(), entry.getValue(), cacheEnabled);
                // CHECKSTYLE:OFF
            } catch (final RuntimeException ex) {
                // CHECKSTYLE:ON
                if (!cacheEnabled) {
                    storageNodes.values().stream().map(DataSourcePoolDestroyer::new).forEach(DataSourcePoolDestroyer::asyncDestroy);
                }
                throw ex;
            }
            storageNodes.put(storageNodeProps.getName(), dataSource);
            appendStorageUnitNodeMapper(storageUnitNodeMappers, storageNodeProps, entry.getKey(), entry.getValue());
        }
        return new StorageResource(storageNodes, storageUnitNodeMappers);
    }
    
    /**
     * Create storage resource without data source.
     *
     * @param dataSourcePropsMap data source properties map
     * @return created storage resource
     */
    public static StorageResourceWithProperties createStorageResourceWithoutDataSource(final Map<String, DataSourceProperties> dataSourcePropsMap) {
        Map<String, DataSource> storageNodes = new LinkedHashMap<>();
        Map<String, StorageUnitNodeMapper> storageUnitNodeMappers = new LinkedHashMap<>();
        Map<String, DataSourceProperties> dataSourcePropertiesMap = new LinkedHashMap<>();
        for (Entry<String, DataSourceProperties> entry : dataSourcePropsMap.entrySet()) {
            StorageNodeProperties storageNodeProperties = getStorageNodeProperties(entry.getKey(), entry.getValue());
            if (storageNodes.containsKey(storageNodeProperties.getName())) {
                appendStorageUnitNodeMapper(storageUnitNodeMappers, storageNodeProperties, entry.getKey(), entry.getValue());
                continue;
            }
            storageNodes.put(storageNodeProperties.getName(), null);
            appendStorageUnitNodeMapper(storageUnitNodeMappers, storageNodeProperties, entry.getKey(), entry.getValue());
            dataSourcePropertiesMap.put(storageNodeProperties.getName(), entry.getValue());
        }
        return new StorageResourceWithProperties(storageNodes, storageUnitNodeMappers, dataSourcePropertiesMap);
    }
    
    private static void appendStorageUnitNodeMapper(final Map<String, StorageUnitNodeMapper> storageUnitNodeMappers, final StorageNodeProperties storageNodeProps,
                                                    final String unitName, final DataSourceProperties dataSourceProps) {
        String url = dataSourceProps.getConnectionPropertySynonyms().getStandardProperties().get("url").toString();
        storageUnitNodeMappers.put(unitName, getStorageUnitNodeMapper(storageNodeProps, unitName, url));
    }
    
    private static StorageUnitNodeMapper getStorageUnitNodeMapper(final StorageNodeProperties storageNodeProps, final String unitName, final String url) {
        DialectDatabaseMetaData dialectDatabaseMetaData = new DatabaseTypeRegistry(storageNodeProps.getDatabaseType()).getDialectDatabaseMetaData();
        return dialectDatabaseMetaData.isInstanceConnectionAvailable()
                ? new StorageUnitNodeMapper(unitName, storageNodeProps.getName(), storageNodeProps.getDatabase(), url)
                : new StorageUnitNodeMapper(unitName, storageNodeProps.getName(), url);
    }
    
    private static StorageNodeProperties getStorageNodeProperties(final String dataSourceName, final DataSourceProperties storageNodeProps) {
        Map<String, Object> standardProperties = storageNodeProps.getConnectionPropertySynonyms().getStandardProperties();
        String url = standardProperties.get("url").toString();
        String username = standardProperties.get("username").toString();
        DatabaseType databaseType = DatabaseTypeFactory.get(url);
        return getStorageNodeProperties(dataSourceName, storageNodeProps, url, username, databaseType);
    }
    
    private static StorageNodeProperties getStorageNodeProperties(final String dataSourceName, final DataSourceProperties dataSourceProps,
                                                                  final String url, final String username, final DatabaseType databaseType) {
        try {
            JdbcUrl jdbcUrl = new StandardJdbcUrlParser().parse(url);
            DialectDatabaseMetaData dialectDatabaseMetaData = new DatabaseTypeRegistry(databaseType).getDialectDatabaseMetaData();
            String nodeName = dialectDatabaseMetaData.isInstanceConnectionAvailable() ? generateStorageNodeName(jdbcUrl.getHostname(), jdbcUrl.getPort(), username) : dataSourceName;
            return new StorageNodeProperties(nodeName, databaseType, dataSourceProps, jdbcUrl.getDatabase());
        } catch (final UnrecognizedDatabaseURLException ex) {
            return new StorageNodeProperties(dataSourceName, databaseType, dataSourceProps, null);
        }
    }
    
    private static String generateStorageNodeName(final String hostname, final int port, final String username) {
        return String.format("%s_%s_%s", hostname, port, username);
    }
    
    /**
     * Create data sources.
     *
     * @param dataSourcePropsMap data source properties map
     * @return created data sources
     */
    public static Map<String, DataSource> create(final Map<String, DataSourceProperties> dataSourcePropsMap) {
        return create(dataSourcePropsMap, true);
    }
    
    /**
     * Create data sources.
     *
     * @param dataSourcePropsMap data source properties map
     * @param cacheEnabled cache enabled
     * @return created data sources
     */
    public static Map<String, DataSource> create(final Map<String, DataSourceProperties> dataSourcePropsMap, final boolean cacheEnabled) {
        Map<String, DataSource> result = new LinkedHashMap<>();
        for (Entry<String, DataSourceProperties> entry : dataSourcePropsMap.entrySet()) {
            DataSource dataSource;
            try {
                dataSource = create(entry.getKey(), entry.getValue(), cacheEnabled);
                // CHECKSTYLE:OFF
            } catch (final RuntimeException ex) {
                // CHECKSTYLE:ON
                if (!cacheEnabled) {
                    result.values().stream().map(DataSourcePoolDestroyer::new).forEach(DataSourcePoolDestroyer::asyncDestroy);
                }
                throw ex;
            }
            result.put(entry.getKey(), dataSource);
        }
        return result;
    }
    
    /**
     * Create data source.
     *
     * @param dataSourceProps data source properties
     * @return created data source
     */
    public static DataSource create(final DataSourceProperties dataSourceProps) {
        DataSource result = createDataSource(dataSourceProps.getDataSourceClassName());
        Optional<DataSourcePoolMetaData> poolMetaData = TypedSPILoader.findService(DataSourcePoolMetaData.class, dataSourceProps.getDataSourceClassName());
        DataSourceReflection dataSourceReflection = new DataSourceReflection(result);
        if (poolMetaData.isPresent()) {
            setDefaultFields(dataSourceReflection, poolMetaData.get());
            setConfiguredFields(dataSourceProps, dataSourceReflection, poolMetaData.get());
            appendJdbcUrlProperties(dataSourceProps.getCustomDataSourceProperties(), result, poolMetaData.get());
            dataSourceReflection.addDefaultDataSourceProperties();
        } else {
            setConfiguredFields(dataSourceProps, dataSourceReflection);
        }
        return result;
    }
    
    /**
     * Create data source.
     *
     * @param dataSourceName data source name
     * @param dataSourceProps data source properties
     * @param cacheEnabled cache enabled
     * @return created data source
     */
    public static DataSource create(final String dataSourceName, final DataSourceProperties dataSourceProps, final boolean cacheEnabled) {
        DataSource result = create(dataSourceProps);
        if (cacheEnabled && !GlobalDataSourceRegistry.getInstance().getCachedDataSources().containsKey(dataSourceName)) {
            GlobalDataSourceRegistry.getInstance().getCachedDataSources().put(dataSourceName, result);
        }
        return result;
    }
    
    @SneakyThrows(ReflectiveOperationException.class)
    private static DataSource createDataSource(final String dataSourceClassName) {
        return (DataSource) Class.forName(dataSourceClassName).getConstructor().newInstance();
    }
    
    private static void setDefaultFields(final DataSourceReflection dataSourceReflection, final DataSourcePoolMetaData poolMetaData) {
        for (Entry<String, Object> entry : poolMetaData.getDefaultProperties().entrySet()) {
            dataSourceReflection.setField(entry.getKey(), entry.getValue());
        }
    }
    
    private static void setConfiguredFields(final DataSourceProperties dataSourceProps, final DataSourceReflection dataSourceReflection) {
        for (Entry<String, Object> entry : dataSourceProps.getAllLocalProperties().entrySet()) {
            dataSourceReflection.setField(entry.getKey(), entry.getValue());
        }
    }
    
    private static void setConfiguredFields(final DataSourceProperties dataSourceProps, final DataSourceReflection dataSourceReflection, final DataSourcePoolMetaData poolMetaData) {
        for (Entry<String, Object> entry : dataSourceProps.getAllLocalProperties().entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();
            if (isValidProperty(fieldName, fieldValue, poolMetaData) && !fieldName.equals(poolMetaData.getFieldMetaData().getJdbcUrlPropertiesFieldName())) {
                dataSourceReflection.setField(fieldName, fieldValue);
            }
        }
    }
    
    private static boolean isValidProperty(final String key, final Object value, final DataSourcePoolMetaData poolMetaData) {
        return !poolMetaData.getInvalidProperties().containsKey(key) || null == value || !value.equals(poolMetaData.getInvalidProperties().get(key));
    }
    
    @SuppressWarnings("unchecked")
    private static void appendJdbcUrlProperties(final CustomDataSourceProperties customDataSourceProps, final DataSource targetDataSource, final DataSourcePoolMetaData poolMetaData) {
        String jdbcUrlPropertiesFieldName = poolMetaData.getFieldMetaData().getJdbcUrlPropertiesFieldName();
        if (null != jdbcUrlPropertiesFieldName && customDataSourceProps.getProperties().containsKey(jdbcUrlPropertiesFieldName)) {
            Map<String, Object> jdbcUrlProps = (Map<String, Object>) customDataSourceProps.getProperties().get(jdbcUrlPropertiesFieldName);
            DataSourcePoolMetaDataReflection dataSourcePoolMetaDataReflection = new DataSourcePoolMetaDataReflection(targetDataSource, poolMetaData.getFieldMetaData());
            for (Entry<String, Object> entry : jdbcUrlProps.entrySet()) {
                dataSourcePoolMetaDataReflection.getJdbcConnectionProperties().ifPresent(optional -> optional.setProperty(entry.getKey(), entry.getValue().toString()));
            }
        }
    }
}
