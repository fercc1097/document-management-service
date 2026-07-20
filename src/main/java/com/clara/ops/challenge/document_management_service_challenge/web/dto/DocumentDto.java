package com.clara.ops.challenge.document_management_service_challenge.web.dto;

import java.util.List;

public record DocumentDto(
    String id,
    String user,
    String name,
    List<String> tags,
    Integer size,
    String type,
    String createdAt) {}
