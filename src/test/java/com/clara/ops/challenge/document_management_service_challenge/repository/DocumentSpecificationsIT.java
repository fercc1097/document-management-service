package com.clara.ops.challenge.document_management_service_challenge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.domain.*;
import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;

class DocumentSpecificationsIT extends IntegrationTest {

  @Autowired DocumentRepository documents;

  @BeforeEach
  void seed() {
    documents.deleteAll();
    documents.save(doc("alice", "invoice-jan.pdf", DocumentStatus.COMPLETED));
    documents.save(doc("alice", "report.pdf", DocumentStatus.COMPLETED));
    documents.save(doc("bob", "invoice-feb.pdf", DocumentStatus.PENDING));
  }

  @Test
  void filtersByUserAndNamePrefixAndExcludesPending() {
    Page<DocumentEntity> page = documents.findAll(
        DocumentSpecifications.filter("alice", "invoice", null),
        PageRequest.of(0, 10));
    assertThat(page.getContent()).extracting(DocumentEntity::getName).containsExactly("invoice-jan.pdf");
  }

  @Test
  void noFiltersReturnsOnlyCompleted() {
    Page<DocumentEntity> page = documents.findAll(
        DocumentSpecifications.filter(null, null, null), PageRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  private DocumentEntity doc(String user, String name, DocumentStatus status) {
    DocumentEntity d = new DocumentEntity();
    d.setId(UUID.randomUUID()); d.setUser(user); d.setName(name);
    d.setMinioPath(user + "/" + name); d.setStatus(status);
    return d;
  }
}
