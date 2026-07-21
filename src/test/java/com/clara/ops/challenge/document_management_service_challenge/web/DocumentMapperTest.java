package com.clara.ops.challenge.document_management_service_challenge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.TagEntity;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.DocumentDto;
import com.clara.ops.challenge.document_management_service_challenge.web.dto.PaginatedDocumentSearch;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DocumentMapperTest {

  private final DocumentMapper mapper = new DocumentMapper();

  private DocumentEntity entity(UUID id, Instant createdAt) {
    DocumentEntity d = new DocumentEntity();
    d.setId(id);
    d.setUser("alice");
    d.setName("doc1.pdf");
    d.setSize(123L);
    d.setFileType("application/pdf");
    d.setCreatedAt(createdAt);
    d.setTags(Set.of(new TagEntity(1L, "invoice")));
    return d;
  }

  @Test
  void mapsAllFields() {
    UUID id = UUID.randomUUID();
    Instant created = Instant.parse("2026-07-20T10:15:30.00Z");
    DocumentDto dto = mapper.toDto(entity(id, created));

    assertThat(dto.id()).isEqualTo(id.toString());
    assertThat(dto.user()).isEqualTo("alice");
    assertThat(dto.name()).isEqualTo("doc1.pdf");
    assertThat(dto.size()).isEqualTo(123L);
    assertThat(dto.type()).isEqualTo("application/pdf");
    assertThat(dto.tags()).containsExactly("invoice");
    assertThat(dto.createdAt()).isEqualTo(created.toString());
  }

  @Test
  void nullCreatedAtMapsToNull() {
    DocumentDto dto = mapper.toDto(entity(UUID.randomUUID(), null));
    assertThat(dto.createdAt()).isNull();
  }

  @Test
  void toResponseBuildsPaginationMetadata() {
    Page<DocumentEntity> page =
        new PageImpl<>(List.of(entity(UUID.randomUUID(), Instant.now())), PageRequest.of(0, 20), 1);
    PaginatedDocumentSearch response = mapper.toResponse(page);

    assertThat(response.documents()).hasSize(1);
    assertThat(response.metadata().currentPage()).isEqualTo(0);
    assertThat(response.metadata().itemsPerPage()).isEqualTo(20);
    assertThat(response.metadata().totalItems()).isEqualTo(1);
    assertThat(response.metadata().totalPages()).isEqualTo(1);
  }
}
