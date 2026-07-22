package com.clara.ops.challenge.document_management_service_challenge.web;

import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.service.DocumentService;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.DocumentDownloadUrl;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.DocumentSearchFilters;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.PaginatedDocumentSearch;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.UploadDocumentRequest;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.UploadDocumentResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/document-management")
public class DocumentController {

  /** Upper bound on page size so a single request can't materialize an unbounded result set. */
  private static final int MAX_PAGE_SIZE = 100;

  private final DocumentService service;
  private final DocumentMapper mapper;
  private final SortCriteriaParser sortParser;

  public DocumentController(
      DocumentService service, DocumentMapper mapper, SortCriteriaParser sortParser) {
    this.service = service;
    this.mapper = mapper;
    this.sortParser = sortParser;
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
      HttpServletRequest request) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    // Read raw values: binding to List<String> makes Spring split "createdAt,desc" on the comma,
    // turning the direction into a phantom property. getParameterValues keeps each criterion whole,
    // so "property,direction" (and repeated ?sort= for multi-sort) reach the parser intact.
    String[] rawSort = request.getParameterValues("sort");
    List<String> sort = rawSort == null ? List.of() : List.of(rawSort);
    Pageable pageable = PageRequest.of(safePage, safeSize, sortParser.parse(sort));
    Page<DocumentEntity> result =
        service.search(filters.user(), filters.name(), filters.tags(), pageable);
    return mapper.toResponse(result);
  }

  @GetMapping("/download/{documentId}")
  public DocumentDownloadUrl download(@PathVariable UUID documentId) {
    return new DocumentDownloadUrl(service.downloadUrl(documentId));
  }
}
