package com.example.core.memory.service;

import java.util.Random;

public class SimpleEmbeddingService implements EmbeddingService {

    private static final int DEFAULT_DIMENSION = 128;

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DEFAULT_DIMENSION];
        if (text == null || text.isEmpty()) {
            return vector;
        }
        long seed = hashText(text);
        Random random = new Random(seed);
        for (int i = 0; i < DEFAULT_DIMENSION; i++) {
            vector[i] = random.nextFloat() * 2 - 1;
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int getDimension() {
        return DEFAULT_DIMENSION;
    }

    private long hashText(String text) {
        long hash = 0;
        for (int i = 0; i < text.length(); i++) {
            hash = 31 * hash + text.charAt(i);
        }
        return hash;
    }

    private void normalize(float[] vector) {
        float sum = 0;
        for (float v : vector) {
            sum += v * v;
        }
        float norm = (float) Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }
}
