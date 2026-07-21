package com.clara.ops.challenge.document_management_service_challenge.web;

import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.TagEntity;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.DocumentDto;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.PaginatedDocumentSearch;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.PaginationMetadata;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Maps {@link DocumentEntity} instances to their outbound web DTOs. */
@Component
public class DocumentMapper {

  public PaginatedDocumentSearch toResponse(Page<DocumentEntity> page) {
    List<DocumentDto> docs = page.getContent().stream().map(this::toDto).toList();
    PaginationMetadata meta =
        new PaginationMetadata(
            page.getNumber(),
            page.getSize(),
            page.getNumberOfElements(),
            page.getTotalPages(),
            (int) page.getTotalElements());
    return new PaginatedDocumentSearch(meta, docs);
  }

  public DocumentDto toDto(DocumentEntity d) {
    return new DocumentDto(
        d.getId().toString(),
        d.getUser(),
        d.getName(),
        d.getTags().stream().map(TagEntity::getName).collect(Collectors.toList()),
        d.getSize(),
        d.getFileType(),
        d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
  }
}
