package com.clara.ops.challenge.document_management_service_challenge.web;

import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.TagEntity;
import com.clara.ops.challenge.document_management_service_challenge.service.DocumentService;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.*;
import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/document-management")
public class DocumentController {

  private final DocumentService service;

  public DocumentController(DocumentService service) {
    this.service = service;
  }

  @PostMapping("/upload")
  public ResponseEntity<UploadDocumentResponse> upload(
      @Valid @RequestBody UploadDocumentRequest req) {
    DocumentService.RegisterResult r = service.register(req.user(), req.name(), req.tags());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new UploadDocumentResponse(r.id(), r.uploadUrl()));
  }

  @PostMapping("/upload/{id}/complete")
  public ResponseEntity<Void> complete(@PathVariable UUID id) {
    service.complete(id);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/search")
  public PaginatedDocumentSearch search(
      @RequestBody DocumentSearchFilters filters,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) List<String> sort) {
    Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
    Page<DocumentEntity> result =
        service.search(filters.user(), filters.name(), filters.tags(), pageable);
    return toResponse(result);
  }

  private Sort resolveSort(List<String> sort) {
    if (sort == null || sort.isEmpty()) {
      return Sort.by(Sort.Direction.DESC, "createdAt");
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (String criterion : sort) {
      String[] parts = criterion.split(",", 2);
      String property = parts[0];
      Sort.Direction direction =
          parts.length > 1 ? Sort.Direction.fromString(parts[1].trim()) : Sort.Direction.ASC;
      orders.add(new Sort.Order(direction, property.trim()));
    }
    return Sort.by(orders);
  }

  @GetMapping("/download/{documentId}")
  public DocumentDownloadUrl download(@PathVariable String documentId) {
    return new DocumentDownloadUrl(service.downloadUrl(UUID.fromString(documentId)));
  }

  private PaginatedDocumentSearch toResponse(Page<DocumentEntity> page) {
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

  private DocumentDto toDto(DocumentEntity d) {
    return new DocumentDto(
        d.getId().toString(),
        d.getUser(),
        d.getName(),
        d.getTags().stream().map(TagEntity::getName).collect(Collectors.toList()),
        d.getSize() == null ? null : d.getSize().intValue(),
        d.getFileType(),
        d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
  }
}
