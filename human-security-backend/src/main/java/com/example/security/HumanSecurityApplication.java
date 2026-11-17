package com.example.security;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.security", "com.example.securityagent"})
@MapperScan({"com.example.security.mapper", "com.example.securityagent.mapper"})
public class HumanSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(HumanSecurityApplication.class, args);
    }
}
