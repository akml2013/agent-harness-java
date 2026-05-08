package com.example.core.memory.service;

public interface EmbeddingService {

    float[] embed(String text);

    int getDimension();
}
