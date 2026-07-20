package com.clara.ops.challenge.document_management_service_challenge.web.error;

import java.util.List;

public record ErrorResponse(int status, String error, List<String> messages) {}
