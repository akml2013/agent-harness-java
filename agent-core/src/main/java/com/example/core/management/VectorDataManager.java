package com.example.core.management;

import java.util.List;
import java.util.Map;

public interface VectorDataManager extends DataManager {

    boolean collectionExists(String collectionName);

    void createCollection(String collectionName, VectorCollectionSchema schema);

    void dropCollection(String collectionName);

    String insert(String collectionName, List<Map<String, Object>> records);

    void deleteByFilter(String collectionName, String filter);

    void deleteByIds(String collectionName, List<String> ids);

    List<VectorSearchResult> search(String collectionName, VectorSearchRequest request);

    List<Map<String, Object>> query(String collectionName, String filter, List<String> outputFields);
}
