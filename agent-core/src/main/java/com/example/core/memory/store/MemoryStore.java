package com.example.core.memory.store;

import java.util.List;

import com.example.core.memory.model.MemoryFragment;
import com.example.core.memory.model.MemoryMessage;

public interface MemoryStore {

    void store(MemoryMessage message);

    void storeBatch(List<MemoryMessage> messages);

    List<MemoryFragment> retrieve(String sessionId, int limit);

    void delete(String sessionId);

    boolean exists(String sessionId);

    default List<MemoryFragment> searchByVector(String query, int topK) {
        return searchByVector(query, topK, null);
    }

    default List<MemoryFragment> searchByVector(String query, int topK, Long userId) {
        return searchByVector(query, topK);
    }
}
