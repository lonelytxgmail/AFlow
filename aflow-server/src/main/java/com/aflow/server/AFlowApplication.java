package com.aflow.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AFlow 服务端启动类。
 * <p>
 * - {@code @EnableAsync}：启用异步方法执行（配合虚拟线程），用于流程异步执行
 * - {@code scanBasePackages = "com.aflow"}：扫描所有模块的 Spring 组件
 * - {@code @EntityScan}：指定 JPA 实体扫描路径
 * - {@code @EnableJpaRepositories}：指定 JPA Repository 扫描路径
 */
@SpringBootApplication(scanBasePackages = "com.aflow")
@EntityScan(basePackages = "com.aflow.persistence.entity")
@EnableJpaRepositories(basePackages = "com.aflow.persistence.repository")
@EnableAsync
@EnableScheduling
public class AFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(AFlowApplication.class, args);
    }
}
