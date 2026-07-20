package com.clara.ops.challenge.document_management_service_challenge.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.clara.ops.challenge.document_management_service_challenge.config.MinioProperties;
import com.clara.ops.challenge.document_management_service_challenge.domain.*;
import com.clara.ops.challenge.document_management_service_challenge.exception.*;
import com.clara.ops.challenge.document_management_service_challenge.repository.*;
import com.clara.ops.challenge.document_management_service_challenge.storage.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

class DocumentServiceTest {

  @Mock DocumentRepository documents;
  @Mock TagRepository tags;
  @Mock StoragePort storage;
  DocumentService service;
  MinioProperties props =
      new MinioProperties("http://i", "http://p", "a", "s", "document-bucket", 3600, 3600, 100L);

  @BeforeEach
  void init() {
    MockitoAnnotations.openMocks(this);
    service = new DocumentService(documents, tags, storage, props);
    when(tags.findByName(anyString())).thenReturn(Optional.empty());
    when(tags.save(any())).thenAnswer(i -> i.getArgument(0));
    when(documents.save(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void registerCreatesPendingAndReturnsPresignedUrl() {
    when(storage.presignedPutUrl("bob/f.pdf")).thenReturn("http://put");

    DocumentService.RegisterResult r = service.register("bob", "f.pdf", List.of("x"));

    assertThat(r.uploadUrl()).isEqualTo("http://put");
    ArgumentCaptor<DocumentEntity> cap = ArgumentCaptor.forClass(DocumentEntity.class);
    verify(documents).save(cap.capture());
    assertThat(cap.getValue().getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(cap.getValue().getMinioPath()).isEqualTo("bob/f.pdf");
  }

  @Test
  void completeFillsSizeAndTypeAndMarksCompleted() {
    UUID id = UUID.randomUUID();
    DocumentEntity d = pending(id, "bob/f.pdf");
    when(documents.findById(id)).thenReturn(Optional.of(d));
    when(storage.stat("bob/f.pdf"))
        .thenReturn(Optional.of(new StoredObject(50L, "application/pdf")));

    service.complete(id);

    assertThat(d.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
    assertThat(d.getSize()).isEqualTo(50L);
    assertThat(d.getFileType()).isEqualTo("application/pdf");
  }

  @Test
  void completeMissingObjectThrowsNotReady() {
    UUID id = UUID.randomUUID();
    when(documents.findById(id)).thenReturn(Optional.of(pending(id, "bob/f.pdf")));
    when(storage.stat("bob/f.pdf")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.complete(id)).isInstanceOf(DocumentNotReadyException.class);
  }

  @Test
  void completeOversizeRemovesObjectAndThrows() {
    UUID id = UUID.randomUUID();
    when(documents.findById(id)).thenReturn(Optional.of(pending(id, "bob/f.pdf")));
    when(storage.stat("bob/f.pdf"))
        .thenReturn(Optional.of(new StoredObject(101L, "application/pdf")));
    assertThatThrownBy(() -> service.complete(id)).isInstanceOf(InvalidDocumentException.class);
    verify(storage).remove("bob/f.pdf");
  }

  @Test
  void completeUnknownIdThrowsNotFound() {
    UUID id = UUID.randomUUID();
    when(documents.findById(id)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.complete(id)).isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void downloadUrlReturnsPresignedGetForCompleted() {
    UUID id = UUID.randomUUID();
    DocumentEntity d = pending(id, "bob/f.pdf");
    d.setStatus(DocumentStatus.COMPLETED);
    when(documents.findById(id)).thenReturn(Optional.of(d));
    when(storage.presignedGetUrl("bob/f.pdf")).thenReturn("http://get");
    assertThat(service.downloadUrl(id)).isEqualTo("http://get");
  }

  @Test
  void downloadUrlForPendingThrowsNotReady() {
    UUID id = UUID.randomUUID();
    when(documents.findById(id)).thenReturn(Optional.of(pending(id, "bob/f.pdf")));
    assertThatThrownBy(() -> service.downloadUrl(id)).isInstanceOf(DocumentNotReadyException.class);
  }

  private DocumentEntity pending(UUID id, String path) {
    DocumentEntity d = new DocumentEntity();
    d.setId(id);
    d.setUser("bob");
    d.setName("f.pdf");
    d.setMinioPath(path);
    d.setStatus(DocumentStatus.PENDING);
    return d;
  }
}
