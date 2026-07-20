package com.clara.ops.challenge.document_management_service_challenge.web.dto;

import java.util.UUID;

public record UploadDocumentResponse(UUID id, String uploadUrl) {}
