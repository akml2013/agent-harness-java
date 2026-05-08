package com.example.core.service;

public interface SessionTitleService {

    String generateTitle(String userMessage);

    void generateAndApplyTitle(String sessionId, Long userId, String userMessage);
}
