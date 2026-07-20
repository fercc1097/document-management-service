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
        // Pin the region so presigned-URL signing stays fully offline: without it the SDK
        // issues a getBucketLocation call to resolve the region, which fails for the public
        // endpoint (localhost:9000 is unreachable from inside the service container).
        .region("us-east-1")
        .build();
  }

  @Bean("publicMinioClient")
  MinioClient publicMinioClient(MinioProperties p) {
    return MinioClient.builder()
        .endpoint(p.publicEndpoint())
        .credentials(p.accessKey(), p.secretKey())
        // Pin the region so presigned-URL signing stays fully offline: without it the SDK
        // issues a getBucketLocation call to resolve the region, which fails for the public
        // endpoint (localhost:9000 is unreachable from inside the service container).
        .region("us-east-1")
        .build();
  }
}
