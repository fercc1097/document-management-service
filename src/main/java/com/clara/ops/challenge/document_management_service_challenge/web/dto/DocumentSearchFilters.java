package com.clara.ops.challenge.document_management_service_challenge.web.dto;

import java.util.List;

public record DocumentSearchFilters(String user, String name, List<String> tags) {}
