package com.clara.ops.challenge.document_management_service_challenge.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record UploadDocumentRequest(
    @NotBlank String user,
    @NotBlank @Pattern(regexp = ".*\\.pdf", message = "name must end with .pdf") String name,
    @NotNull List<@NotBlank String> tags) {}
