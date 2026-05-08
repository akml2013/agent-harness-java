package com.example.core.management.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.core.management.DataManager;
import com.example.core.management.VectorCollectionSchema;
import com.example.core.management.VectorDataManager;
import com.example.core.management.VectorFieldSchema;
import com.example.core.management.VectorIndexSchema;
import com.example.core.management.VectorSearchRequest;
import com.example.core.management.VectorSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "management.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchDataManager implements DataManager, VectorDataManager {

    private final String host;
    private final int port;

    private ElasticsearchClient client;
    private RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ElasticsearchDataManager() {
        this.host = "localhost";
        this.port = 9200;
    }

    public ElasticsearchDataManager(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @PostConstruct
    @Override
    public void initialize() {
        try {
            restClient = RestClient.builder(HttpHost.create(host + ":" + port)).build();
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            client = new ElasticsearchClient(transport);
            log.info("Elasticsearch数据管理器初始化完成: {}:{}", host, port);
        } catch (Exception e) {
            log.error("Elasticsearch数据管理器初始化失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    @Override
    public void destroy() {
        if (restClient != null) {
            try {
                restClient.close();
                log.info("Elasticsearch数据管理器已关闭");
            } catch (Exception e) {
                log.warn("Elasticsearch客户端关闭异常: {}", e.getMessage());
            }
        }
    }

    @Override
    public String getType() {
        return "ELASTICSEARCH";
    }

    @Override
    public boolean isAvailable() {
        try {
            if (client == null) {
                return false;
            }
            client.ping();
            return true;
        } catch (Exception e) {
            log.warn("Elasticsearch不可用: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean collectionExists(String indexName) {
        try {
            return client.indices().exists(e -> e.index(indexName)).value();
        } catch (Exception e) {
            log.error("Elasticsearch检查索引存在失败: {}, 错误: {}", indexName, e.getMessage());
            return false;
        }
    }

    @Override
    public void createCollection(String indexName, VectorCollectionSchema schema) {
        try {
            Map<String, Object> properties = new HashMap<>();

            for (VectorFieldSchema field : schema.fields()) {
                Map<String, Object> fieldMapping = new HashMap<>();

                switch (field.dataType().toUpperCase()) {
                    case "KEYWORD" -> fieldMapping.put("type", "keyword");
                    case "TEXT" -> fieldMapping.put("type", "text");
                    case "LONG" -> fieldMapping.put("type", "long");
                    case "INTEGER" -> fieldMapping.put("type", "integer");
                    case "DOUBLE" -> fieldMapping.put("type", "double");
                    case "BOOLEAN" -> fieldMapping.put("type", "boolean");
                    case "DENSE_VECTOR" -> {
                        fieldMapping.put("type", "dense_vector");
                        if (field.dimension() != null) {
                            fieldMapping.put("dims", field.dimension());
                        }
                        fieldMapping.put("index", true);
                        for (VectorIndexSchema index : schema.indexes()) {
                            if (index.fieldName().equals(field.name())) {
                                fieldMapping.put("similarity",
                                        mapSimilarity(index.metricType()));
                                break;
                            }
                        }
                    }
                    default -> fieldMapping.put("type", field.dataType().toLowerCase());
                }

                properties.put(field.name(), fieldMapping);
            }

            client.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> {
                        for (Map.Entry<String, Object> entry : properties.entrySet()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> prop = (Map<String, Object>) entry.getValue();
                            m.properties(entry.getKey(), p -> {
                                if ("dense_vector".equals(prop.get("type"))) {
                                    return p.denseVector(dv -> {
                                        dv.dims((Integer) prop.get("dims"));
                                        dv.index((Boolean) prop.getOrDefault("index", true));
                                        if (prop.containsKey("similarity")) {
                                            dv.similarity((String) prop.get("similarity"));
                                        }
                                        return dv;
                                    });
                                } else if ("keyword".equals(prop.get("type"))) {
                                    return p.keyword(k -> k);
                                } else if ("text".equals(prop.get("type"))) {
                                    return p.text(t -> t);
                                } else if ("long".equals(prop.get("type"))) {
                                    return p.long_(l -> l);
                                } else if ("integer".equals(prop.get("type"))) {
                                    return p.integer(i -> i);
                                } else if ("double".equals(prop.get("type"))) {
                                    return p.double_(d -> d);
                                } else if ("boolean".equals(prop.get("type"))) {
                                    return p.boolean_(b -> b);
                                }
                                return p.keyword(k -> k);
                            });
                        }
                        return m;
                    }));

            log.info("Elasticsearch索引创建成功: {}", indexName);
        } catch (Exception e) {
            log.error("Elasticsearch索引创建失败: {}, 错误: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch索引创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void dropCollection(String indexName) {
        try {
            client.indices().delete(d -> d.index(indexName));
            log.info("Elasticsearch索引删除成功: {}", indexName);
        } catch (Exception e) {
            log.error("Elasticsearch索引删除失败: {}, 错误: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch索引删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String insert(String indexName, List<Map<String, Object>> records) {
        try {
            List<BulkOperation> operations = new ArrayList<>();

            for (Map<String, Object> record : records) {
                Object id = record.remove("_id");
                if (id != null) {
                    String docId = id.toString();
                    operations.add(BulkOperation.of(idx -> idx
                            .index(ind -> ind
                                    .index(indexName)
                                    .id(docId)
                                    .document(record))));
                } else {
                    operations.add(BulkOperation.of(idx -> idx
                            .index(ind -> ind
                                    .index(indexName)
                                    .document(record))));
                }
            }

            BulkResponse bulkResponse = client.bulk(b -> b
                    .operations(operations)
                    .refresh(Refresh.True));

            if (bulkResponse.errors()) {
                log.warn("Elasticsearch批量插入部分失败: index={}", indexName);
            }

            long count = bulkResponse.items().size();
            log.debug("Elasticsearch插入成功: index={}, count={}", indexName, count);
            return String.valueOf(count);
        } catch (Exception e) {
            log.error("Elasticsearch插入失败: index={}, 错误: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch插入失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByFilter(String indexName, String filter) {
        try {
            client.deleteByQuery(d -> d
                    .index(indexName)
                    .query(q -> q
                            .queryString(qs -> qs
                                    .query(filter)))
                    .refresh(true));
            log.debug("Elasticsearch按过滤条件删除成功: index={}", indexName);
        } catch (Exception e) {
            log.error("Elasticsearch按过滤条件删除失败: index={}, 错误: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteByIds(String indexName, List<String> ids) {
        try {
            List<BulkOperation> operations = new ArrayList<>();
            for (String id : ids) {
                operations.add(BulkOperation.of(del -> del
                        .delete(d -> d
                                .index(indexName)
                                .id(id))));
            }

            client.bulk(b -> b
                    .operations(operations)
                    .refresh(Refresh.True));

            log.debug("Elasticsearch按ID删除成功: index={}, ids={}", indexName, ids.size());
        } catch (Exception e) {
            log.error("Elasticsearch按ID删除失败: index={}, 错误: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch按ID删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<VectorSearchResult> search(String indexName, VectorSearchRequest request) {
        try {
            String vectorFieldName = "embedding";
            if (request.outputFields() != null && !request.outputFields().isEmpty()) {
                for (String field : request.outputFields()) {
                    if (field.contains("embedding") || field.contains("vector")) {
                        vectorFieldName = field;
                        break;
                    }
                }
            }

            final String finalVectorField = vectorFieldName;

            SearchResponse<Map> response = client.search(s -> {
                s.index(indexName);

                if (request.queryVector() != null && !request.queryVector().isEmpty()) {
                    s.knn(knn -> knn
                            .field(finalVectorField)
                            .queryVector(request.queryVector())
                            .k(request.topK())
                            .numCandidates(request.topK() * 2));
                }

                if (request.filter() != null && !request.filter().isBlank()) {
                    s.query(q -> q
                            .queryString(qs -> qs
                                    .query(request.filter())));
                }

                s.size(request.topK());
                return s;
            }, Map.class);

            List<VectorSearchResult> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> fields = hit.source() != null ? hit.source() : new HashMap<>();
                fields.remove("embedding");
                results.add(new VectorSearchResult(
                        hit.id(),
                        hit.score() != null ? hit.score() : 0.0,
                        fields));
            }

            log.debug("Elasticsearch向量搜索完成: index={}, results={}", indexName, results.size());
            return results;
        } catch (Exception e) {
            log.error("Elasticsearch向量搜索失败: index={}, 错误: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch向量搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> query(String indexName, String filter, List<String> outputFields) {
        try {
            SearchResponse<Map> response = client.search(s -> {
                s.index(indexName);
                if (filter != null && !filter.isBlank()) {
                    s.query(q -> q.queryString(qs -> qs.query(filter)));
                }
                if (outputFields != null && !outputFields.isEmpty()) {
                    s.source(src -> src.filter(f -> f.includes(outputFields)));
                }
                s.size(100);
                return s;
            }, Map.class);

            List<Map<String, Object>> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.source() != null) {
                    Map<String, Object> result = new HashMap<>(hit.source());
                    result.put("_id", hit.id());
                    results.add(result);
                }
            }

            log.debug("Elasticsearch查询完成: index={}, results={}", indexName, results.size());
            return results;
        } catch (Exception e) {
            log.error("Elasticsearch查询失败: index={}, 错误: {}", indexName, e.getMessage());
            throw new RuntimeException("Elasticsearch查询失败: " + e.getMessage(), e);
        }
    }

    private String mapSimilarity(String metricType) {
        return switch (metricType.toUpperCase()) {
            case "COSINE" -> "cosine";
            case "L2" -> "l2_norm";
            case "IP" -> "dot_product";
            default -> "cosine";
        };
    }
}
