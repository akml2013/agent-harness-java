package com.example.core.management;

import java.util.List;

public record VectorCollectionSchema(
        List<VectorFieldSchema> fields,
        List<VectorIndexSchema> indexes) {
}
