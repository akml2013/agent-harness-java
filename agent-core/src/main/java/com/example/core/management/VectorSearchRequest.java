package com.example.core.management;

import java.util.List;

public record VectorSearchRequest(
        List<Float> queryVector,
        int topK,
        String filter,
        List<String> outputFields) {

    public static VectorSearchRequest of(List<Float> queryVector, int topK) {
        return new VectorSearchRequest(queryVector, topK, null, null);
    }

    public static VectorSearchRequest of(List<Float> queryVector, int topK, String filter) {
        return new VectorSearchRequest(queryVector, topK, filter, null);
    }

    public static VectorSearchRequest of(List<Float> queryVector, int topK, String filter, List<String> outputFields) {
        return new VectorSearchRequest(queryVector, topK, filter, outputFields);
    }
}
