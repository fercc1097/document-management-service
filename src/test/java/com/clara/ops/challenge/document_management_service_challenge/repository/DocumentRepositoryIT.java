package com.clara.ops.challenge.document_management_service_challenge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.domain.*;
import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DocumentRepositoryIT extends IntegrationTest {

  @Autowired DocumentRepository documents;
  @Autowired TagRepository tags;

  @Test
  void persistsDocumentWithTags() {
    TagEntity t = tags.save(new TagEntity(null, "invoice"));
    DocumentEntity d = new DocumentEntity();
    d.setId(UUID.randomUUID());
    d.setUser("user1");
    d.setName("doc1.pdf");
    d.setMinioPath("user1/doc1.pdf");
    d.setStatus(DocumentStatus.PENDING);
    d.setTags(Set.of(t));

    DocumentEntity saved = documents.saveAndFlush(d);

    assertThat(documents.findById(saved.getId())).isPresent();
    assertThat(saved.getTags()).extracting(TagEntity::getName).containsExactly("invoice");
  }
}
