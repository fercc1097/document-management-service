package com.clara.ops.challenge.document_management_service_challenge.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

  @Bean("internalMinioClient")
  MinioClient internalMinioClient(MinioProperties p) {
    return MinioClient.builder()
        .endpoint(p.internalEndpoint())
        .credentials(p.accessKey(), p.secretKey())
        .build();
  }

  @Bean("publicMinioClient")
  MinioClient publicMinioClient(MinioProperties p) {
    return MinioClient.builder()
        .endpoint(p.publicEndpoint())
        .credentials(p.accessKey(), p.secretKey())
        .build();
  }
}
