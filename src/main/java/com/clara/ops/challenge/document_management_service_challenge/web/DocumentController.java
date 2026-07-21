package com.clara.ops.challenge.document_management_service_challenge.web;

import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.TagEntity;
import com.clara.ops.challenge.document_management_service_challenge.exception.InvalidDocumentException;
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

  /** Upper bound on page size so a single request can't materialize an unbounded result set. */
  private static final int MAX_PAGE_SIZE = 100;

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
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sort));
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
      Sort.Direction direction = Sort.Direction.ASC;
      if (parts.length > 1) {
        String dir = parts[1].trim();
        direction =
            Sort.Direction.fromOptionalString(dir)
                .orElseThrow(() -> new InvalidDocumentException("Invalid sort direction: " + dir));
      }
      orders.add(new Sort.Order(direction, property.trim()));
    }
    return Sort.by(orders);
  }

  @GetMapping("/download/{documentId}")
  public DocumentDownloadUrl download(@PathVariable UUID documentId) {
    return new DocumentDownloadUrl(service.downloadUrl(documentId));
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
        d.getSize(),
        d.getFileType(),
        d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
  }
}
