package com.example.core.management;

public interface DataManager {

    String getType();

    boolean isAvailable();

    void initialize();

    void destroy();
}
