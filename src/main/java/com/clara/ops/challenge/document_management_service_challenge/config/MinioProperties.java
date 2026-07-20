package com.clara.ops.challenge.document_management_service_challenge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
    String internalEndpoint,
    String publicEndpoint,
    String accessKey,
    String secretKey,
    String bucket,
    int presignedPutExpirySeconds,
    int presignedGetExpirySeconds,
    long maxFileSizeBytes) {}
