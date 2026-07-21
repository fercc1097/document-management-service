package com.clara.ops.challenge.document_management_service_challenge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentStatus;
import com.clara.ops.challenge.document_management_service_challenge.domain.TagEntity;
import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DocumentRepositoryIT extends IntegrationTest {

  @Autowired DocumentRepository documents;
  @Autowired TagRepository tags;

  @BeforeEach
  void cleanDocuments() {
    // Postgres is a shared singleton container across the whole IT suite (see IntegrationTest),
    // so start each test from an empty documents table to stay deterministic regardless of
    // which other IT classes ran before this one.
    documents.deleteAll();
  }

  @Test
  void persistsDocumentWithTags() {
    // Tag name is class-specific to avoid colliding with the unique "tags.name" constraint when
    // other IT classes (e.g. DocumentFlowIT) also create a tag literally named "invoice" in the
    // same shared Postgres instance.
    TagEntity t =
        tags.findByName("invoice-repo-it")
            .orElseGet(() -> tags.save(new TagEntity(null, "invoice-repo-it")));
    DocumentEntity d = new DocumentEntity();
    d.setId(UUID.randomUUID());
    d.setUser("user1");
    d.setName("doc1.pdf");
    d.setMinioPath("user1/doc1.pdf");
    d.setStatus(DocumentStatus.PENDING);
    d.setTags(Set.of(t));

    DocumentEntity saved = documents.saveAndFlush(d);

    assertThat(documents.findById(saved.getId())).isPresent();
    assertThat(saved.getTags()).extracting(TagEntity::getName).containsExactly("invoice-repo-it");
  }
}
