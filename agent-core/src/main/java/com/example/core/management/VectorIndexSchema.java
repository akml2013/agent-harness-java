package com.example.core.management;

public record VectorIndexSchema(
        String fieldName,
        String indexType,
        String metricType) {

    public static VectorIndexSchema autoIndex(String fieldName, String metricType) {
        return new VectorIndexSchema(fieldName, "AUTOINDEX", metricType);
    }

    public static VectorIndexSchema cosineAutoIndex(String fieldName) {
        return new VectorIndexSchema(fieldName, "AUTOINDEX", "COSINE");
    }
}
