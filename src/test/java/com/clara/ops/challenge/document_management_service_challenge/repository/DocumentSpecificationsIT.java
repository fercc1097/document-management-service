package com.clara.ops.challenge.document_management_service_challenge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentEntity;
import com.clara.ops.challenge.document_management_service_challenge.domain.DocumentStatus;
import com.clara.ops.challenge.document_management_service_challenge.domain.TagEntity;
import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class DocumentSpecificationsIT extends IntegrationTest {

  @Autowired DocumentRepository documents;
  @Autowired TagRepository tags;

  @BeforeEach
  void seed() {
    documents.deleteAll();
    documents.save(doc("alice", "invoice-jan.pdf", DocumentStatus.COMPLETED));
    documents.save(doc("alice", "report.pdf", DocumentStatus.COMPLETED));
    documents.save(doc("bob", "invoice-feb.pdf", DocumentStatus.PENDING));
  }

  @Test
  void filtersByUserAndNamePrefixAndExcludesPending() {
    Page<DocumentEntity> page =
        documents.findAll(
            DocumentSpecifications.filter("alice", "invoice", null), PageRequest.of(0, 10));
    assertThat(page.getContent())
        .extracting(DocumentEntity::getName)
        .containsExactly("invoice-jan.pdf");
  }

  @Test
  void noFiltersReturnsOnlyCompleted() {
    Page<DocumentEntity> page =
        documents.findAll(DocumentSpecifications.filter(null, null, null), PageRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  @Test
  void tagFilterRequiresEveryRequestedTag() {
    TagEntity a = tags.save(new TagEntity(null, "spec-and-a"));
    TagEntity b = tags.save(new TagEntity(null, "spec-and-b"));

    DocumentEntity both = doc("carol", "both.pdf", DocumentStatus.COMPLETED);
    both.setTags(new HashSet<>(Set.of(a, b)));
    documents.save(both);

    DocumentEntity onlyOne = doc("carol", "only-a.pdf", DocumentStatus.COMPLETED);
    onlyOne.setTags(new HashSet<>(Set.of(a)));
    documents.save(onlyOne);

    // AND semantics: only the document carrying BOTH tags matches.
    Page<DocumentEntity> page =
        documents.findAll(
            DocumentSpecifications.filter(null, null, List.of("spec-and-a", "spec-and-b")),
            PageRequest.of(0, 10));

    assertThat(page.getContent()).extracting(DocumentEntity::getName).containsExactly("both.pdf");
  }

  @Test
  void nameFilterTreatsLikeMetacharactersLiterally() {
    documents.save(doc("dave", "a%b.pdf", DocumentStatus.COMPLETED));
    documents.save(doc("dave", "axb.pdf", DocumentStatus.COMPLETED));

    // "a%" must match the literal percent, not act as a wildcard (which would also match
    // "axb.pdf").
    Page<DocumentEntity> page =
        documents.findAll(DocumentSpecifications.filter(null, "a%", null), PageRequest.of(0, 10));

    assertThat(page.getContent()).extracting(DocumentEntity::getName).containsExactly("a%b.pdf");
  }

  private DocumentEntity doc(String user, String name, DocumentStatus status) {
    DocumentEntity d = new DocumentEntity();
    d.setId(UUID.randomUUID());
    d.setUser(user);
    d.setName(name);
    d.setMinioPath(user + "/" + name);
    d.setStatus(status);
    return d;
  }
}
