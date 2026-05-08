package com.example.core.management.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.core.management.DataManager;
import com.example.core.management.VectorCollectionSchema;
import com.example.core.management.VectorDataManager;
import com.example.core.management.VectorFieldSchema;
import com.example.core.management.VectorIndexSchema;
import com.example.core.management.VectorSearchRequest;
import com.example.core.management.VectorSearchResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "management.milvus.enabled", havingValue = "true", matchIfMissing = true)
public class MilvusDataManager implements DataManager, VectorDataManager {

    private static final Gson GSON = new Gson();

    private final String uri;

    private MilvusClientV2 milvusClient;

    public MilvusDataManager() {
        this.uri = "http://localhost:19530";
    }

    public MilvusDataManager(String uri) {
        this.uri = uri;
    }

    @PostConstruct
    @Override
    public void initialize() {
        try {
            ConnectConfig config = ConnectConfig.builder()
                    .uri(uri)
                    .build();
            milvusClient = new MilvusClientV2(config);
            log.info("Milvus数据管理器初始化完成: uri={}", uri);
        } catch (Exception e) {
            log.error("Milvus数据管理器初始化失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    @Override
    public void destroy() {
        if (milvusClient != null) {
            try {
                milvusClient.close(10);
                log.info("Milvus数据管理器已关闭");
            } catch (Exception e) {
                log.warn("Milvus客户端关闭异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public String getType() {
        return "MILVUS";
    }

    @Override
    public boolean isAvailable() {
        try {
            if (milvusClient == null) {
                return false;
            }
            milvusClient.listCollections();
            return true;
        } catch (Exception e) {
            log.warn("Milvus不可用: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean collectionExists(String collectionName) {
        try {
            HasCollectionReq req = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            return milvusClient.hasCollection(req);
        } catch (Exception e) {
            log.error("Milvus检查集合存在失败: {}, 错误: {}", collectionName, e.getMessage());
            return false;
        }
    }

    @Override
    public void createCollection(String collectionName, VectorCollectionSchema schema) {
        try {
            CreateCollectionReq.CollectionSchema milvusSchema = CreateCollectionReq.CollectionSchema.builder()
                    .build();

            for (VectorFieldSchema field : schema.fields()) {
                AddFieldReq.AddFieldReqBuilder fieldBuilder = AddFieldReq.builder()
                        .fieldName(field.name())
                        .dataType(mapDataType(field.dataType()))
                        .isPrimaryKey(field.isPrimaryKey())
                        .autoID(field.autoId());

                if (field.maxLength() != null) {
                    fieldBuilder.maxLength(field.maxLength());
                }
                if (field.dimension() != null) {
                    fieldBuilder.dimension(field.dimension());
                }

                milvusSchema.addField(fieldBuilder.build());
            }

            List<IndexParam> indexParams = new ArrayList<>();
            for (VectorIndexSchema index : schema.indexes()) {
                indexParams.add(IndexParam.builder()
                        .fieldName(index.fieldName())
                        .indexType(mapIndexType(index.indexType()))
                        .metricType(mapMetricType(index.metricType()))
                        .build());
            }

            CreateCollectionReq createReq = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(milvusSchema)
                    .indexParams(indexParams)
                    .build();

            milvusClient.createCollection(createReq);
            log.info("Milvus集合创建成功: {}", collectionName);
        } catch (Exception e) {
            log.error("Milvus集合创建失败: {}, 错误: {}", collectionName, e.getMessage());
            throw new RuntimeException("Milvus集合创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void dropCollection(String collectionName) {
        try {
            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            log.info("Milvus集合删除成功: {}", collectionName);
        } catch (Exception e) {
            log.error("Milvus集合删除失败: {}, 错误: {}", collectionName, e.getMessage());
            throw new RuntimeException("Milvus集合删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String insert(String collectionName, List<Map<String, Object>> records) {
        try {
            List<JsonObject> data = new ArrayList<>();
            for (Map<String, Object> record : records) {
                JsonObject row = new JsonObject();
                for (Map.Entry<String, Object> entry : record.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof Number num) {
                        row.addProperty(entry.getKey(), num);
                    } else if (value instanceof String str) {
                        row.addProperty(entry.getKey(), str);
                    } else if (value instanceof Boolean bool) {
                        row.addProperty(entry.getKey(), bool);
                    } else if (value instanceof List<?> list) {
                        row.add(entry.getKey(), GSON.toJsonTree(list));
                    } else if (value instanceof float[] arr) {
                        List<Float> floatList = new ArrayList<>(arr.length);
                        for (float v : arr) {
                            floatList.add(v);
                        }
                        row.add(entry.getKey(), GSON.toJsonTree(floatList));
                    } else if (value != null) {
                        row.add(entry.getKey(), GSON.toJsonTree(value));
                    }
                }
                data.add(row);
            }

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build();
            InsertResp insertResp = milvusClient.insert(insertReq);
            long count = insertResp.getInsertCnt();
            log.debug("Milvus插入成功: collection={}, count={}", collectionName, count);
            return String.valueOf(count);
        } catch (Exception e) {
            log.error("Milvus插入失败: collection={}, 错误: {}", collectionName, e.getMessage());
            throw new RuntimeException("Milvus插入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByFilter(String collectionName, String filter) {
        try {
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter(filter)
                    .build());
            log.debug("Milvus按过滤条件删除成功: collection={}, filter={}", collectionName, filter);
        } catch (Exception e) {
            log.error("Milvus按过滤条件删除失败: collection={}, 错误: {}", collectionName, e.getMessage());
            throw new RuntimeException("Milvus删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByIds(String collectionName, List<String> ids) {
        try {
            List<Object> longIds = new ArrayList<>();
            for (String id : ids) {
                longIds.add(Long.parseLong(id));
            }
            milvusClient.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .ids(longIds)
                    .build());
            log.debug("Milvus按ID删除成功: collection={}, ids={}", collectionName, ids);
        } catch (Exception e) {
            log.error("Milvus按ID删除失败: collection={}, 错误: {}", collectionName, e.getMessage());
            throw new RuntimeException("Milvus按ID删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorSearchResult> search(String collectionName, VectorSearchRequest request) {
        try {
            SearchReq.SearchReqBuilder searchReqBuilder = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(List.of(new FloatVec(request.queryVector())))
                    .topK(request.topK());

            if (request.filter() != null && !request.filter().isBlank()) {
                searchReqBuilder.filter(request.filter());
            }

            if (request.outputFields() != null && !request.outputFields().isEmpty()) {
                searchReqBuilder.outputFields(request.outputFields());
            }

            SearchResp searchResp = milvusClient.search(searchReqBuilder.build());
            List<List<SearchResp.SearchResult>> allResults = searchResp.getSearchResults();

            List<VectorSearchResult> results = new ArrayList<>();
            if (!allResults.isEmpty()) {
                for (SearchResp.SearchResult hit : allResults.get(0)) {
                    Map<String, Object> fields = new HashMap<>();
                    if (hit.getEntity() != null) {
                        fields.putAll(hit.getEntity());
                    }
                    results.add(new VectorSearchResult(
                            String.valueOf(hit.getId()),
                            hit.getScore(),
                            fields));
                }
            }

            log.debug("Milvus向量搜索完成: collection={}, results={}", collectionName, results.size());
            return results;
        } catch (Exception e) {
            log.error("Milvus向量搜索失败: collection={}, 错误: {}", collectionName, e.getMessage());
            throw new RuntimeException("Milvus向量搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> query(String collectionName, String filter, List<String> outputFields) {
        try {
            QueryReq.QueryReqBuilder queryReqBuilder = QueryReq.builder()
                    .collectionName(collectionName)
                    .filter(filter);

            if (outputFields != null && !outputFields.isEmpty()) {
                queryReqBuilder.outputFields(outputFields);
            }

            QueryResp queryResp = milvusClient.query(queryReqBuilder.build());
            List<Map<String, Object>> results = new ArrayList<>();

            for (QueryResp.QueryResult qr : queryResp.getQueryResults()) {
                results.add(new HashMap<>(qr.getEntity()));
            }

            log.debug("Milvus查询完成: collection={}, results={}", collectionName, results.size());
            return results;
        } catch (Exception e) {
            log.error("Milvus查询失败: collection={}, 错误: {}", collectionName, e.getMessage());
            throw new RuntimeException("Milvus查询失败: " + e.getMessage(), e);
        }
    }

    private DataType mapDataType(String dataType) {
        return switch (dataType.toUpperCase()) {
            case "INT64" -> DataType.Int64;
            case "INT32" -> DataType.Int32;
            case "INT16" -> DataType.Int16;
            case "INT8" -> DataType.Int8;
            case "FLOAT" -> DataType.Float;
            case "DOUBLE" -> DataType.Double;
            case "BOOL" -> DataType.Bool;
            case "VARCHAR" -> DataType.VarChar;
            case "FLOAT_VECTOR" -> DataType.FloatVector;
            case "FLOAT16_VECTOR" -> DataType.Float16Vector;
            case "BINARY_VECTOR" -> DataType.BinaryVector;
            default -> throw new IllegalArgumentException("不支持的数据类型: " + dataType);
        };
    }

    private IndexParam.IndexType mapIndexType(String indexType) {
        return switch (indexType.toUpperCase()) {
            case "AUTOINDEX" -> IndexParam.IndexType.AUTOINDEX;
            case "IVF_FLAT" -> IndexParam.IndexType.IVF_FLAT;
            case "IVF_SQ8" -> IndexParam.IndexType.IVF_SQ8;
            case "IVF_PQ" -> IndexParam.IndexType.IVF_PQ;
            case "HNSW" -> IndexParam.IndexType.HNSW;
            case "DISKANN" -> IndexParam.IndexType.DISKANN;
            default -> IndexParam.IndexType.AUTOINDEX;
        };
    }

    private IndexParam.MetricType mapMetricType(String metricType) {
        return switch (metricType.toUpperCase()) {
            case "COSINE" -> IndexParam.MetricType.COSINE;
            case "L2" -> IndexParam.MetricType.L2;
            case "IP" -> IndexParam.MetricType.IP;
            default -> IndexParam.MetricType.COSINE;
        };
    }
}
