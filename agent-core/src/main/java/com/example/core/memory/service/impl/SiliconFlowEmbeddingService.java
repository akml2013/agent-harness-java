package com.example.core.memory.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.example.core.memory.service.EmbeddingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SiliconFlowEmbeddingService implements EmbeddingService {

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int dimension;

    private final RestTemplate restTemplate;

    public SiliconFlowEmbeddingService(String apiUrl, String apiKey, String model, int dimension) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = dimension;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            return new float[dimension];
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "input", text,
                    "encoding_format", "float"
            );

            if (dimension != 2560) {
                requestBody = new java.util.HashMap<>(requestBody);
                requestBody.put("dimensions", dimension);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(apiUrl, entity, Map.class);

            if (response == null) {
                log.error("Embedding API返回null");
                return new float[dimension];
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dataList = (List<Map<String, Object>>) response.get("data");

            if (dataList == null || dataList.isEmpty()) {
                log.error("Embedding API返回数据为空");
                return new float[dimension];
            }

            @SuppressWarnings("unchecked")
            List<Number> embeddingList = (List<Number>) dataList.get(0).get("embedding");

            if (embeddingList == null || embeddingList.isEmpty()) {
                log.error("Embedding API返回向量为空");
                return new float[dimension];
            }

            float[] vector = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                vector[i] = embeddingList.get(i).floatValue();
            }

            log.debug("Embedding生成成功: model={}, dimension={}, inputLength={}",
                    model, vector.length, text.length());

            return vector;

        } catch (Exception e) {
            log.error("Embedding API调用失败: {}", e.getMessage());
            return new float[dimension];
        }
    }

    @Override
    public int getDimension() {
        return dimension;
    }
}
