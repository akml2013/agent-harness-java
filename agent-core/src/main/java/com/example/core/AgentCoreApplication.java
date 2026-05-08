package com.example.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.example.core.mapper")
public class AgentCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentCoreApplication.class, args);
    }
}
