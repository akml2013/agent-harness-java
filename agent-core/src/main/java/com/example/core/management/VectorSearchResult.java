package com.example.core.management;

import java.util.Map;

public record VectorSearchResult(
        String id,
        double score,
        Map<String, Object> fields) {
}
