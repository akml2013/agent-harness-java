package com.example.core.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class DeepseekService {

    @Value("${deepseek.api-key:sk-cc547cd1a3f840869e5a646feda7b3a2}")
    private String apiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-v4-flash}")
    private String model;

    private final ChatModel chatModel;

    private RestTemplate restTemplate;
    private HttpHeaders headers;

    public DeepseekService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(180000);
        restTemplate = new RestTemplate(factory);
        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
    }

    public String chat(String userMessage) {
        return chatWithSystem(null, userMessage);
    }

    public String chatWithSystem(String systemMessage, String userMessage) {
        String url = baseUrl + "/chat/completions";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();

        if (systemMessage != null && !systemMessage.isEmpty()) {
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemMessage);
            messages.add(systemMsg);
        }

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        requestBody.put("messages", messages);
        requestBody.put("stream", false);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject jsonResponse = JSON.parseObject(response.getBody());
                if (jsonResponse != null && jsonResponse.containsKey("choices")) {
                    return jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                }
            }
            return "API调用失败: " + response.getStatusCode();
        } catch (Exception e) {
            return "API调用异常: " + e.getMessage();
        }
    }

    public String chatWithSystemStream(String systemMessage, String userMessage, Consumer<String> chunkConsumer) {
        try {
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();

            if (systemMessage != null && !systemMessage.isEmpty()) {
                messages.add(new SystemMessage(systemMessage));
            }
            messages.add(new UserMessage(userMessage));

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .withModel(model)
                    .build();

            Prompt prompt = new Prompt(messages, options);
            Flux<ChatResponse> stream = chatModel.stream(prompt);

            StringBuilder fullResponse = new StringBuilder();

            stream.doOnNext(chatResponse -> {
                if (chatResponse.getResult() != null
                        && chatResponse.getResult().getOutput() != null
                        && chatResponse.getResult().getOutput().getContent() != null) {
                    String chunk = chatResponse.getResult().getOutput().getContent();
                    if (!chunk.isEmpty()) {
                        fullResponse.append(chunk);
                        if (chunkConsumer != null) {
                            chunkConsumer.accept(chunk);
                        }
                    }
                }
            }).doOnError(e -> {
                log.error("流式调用异常: {}", e.getMessage());
            }).blockLast(Duration.ofSeconds(180));

            return fullResponse.toString();
        } catch (Exception e) {
            log.error("流式调用失败: {}", e.getMessage());
            String fallback = chatWithSystem(systemMessage, userMessage);
            if (chunkConsumer != null) {
                chunkConsumer.accept(fallback);
            }
            return fallback;
        }
    }
}
