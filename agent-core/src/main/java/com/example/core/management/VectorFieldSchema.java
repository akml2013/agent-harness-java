package com.example.core.management;

public record VectorFieldSchema(
        String name,
        String dataType,
        boolean isPrimaryKey,
        boolean autoId,
        Integer maxLength,
        Integer dimension) {

    public static VectorFieldSchema of(String name, String dataType) {
        return new VectorFieldSchema(name, dataType, false, false, null, null);
    }

    public static VectorFieldSchema primaryKey(String name, String dataType, boolean autoId) {
        return new VectorFieldSchema(name, dataType, true, autoId, null, null);
    }

    public static VectorFieldSchema varChar(String name, int maxLength) {
        return new VectorFieldSchema(name, "VARCHAR", false, false, maxLength, null);
    }

    public static VectorFieldSchema vector(String name, int dimension) {
        return new VectorFieldSchema(name, "FLOAT_VECTOR", false, false, null, dimension);
    }

    public static VectorFieldSchema int64(String name) {
        return new VectorFieldSchema(name, "INT64", false, false, null, null);
    }
}
