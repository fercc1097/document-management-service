package com.clara.ops.challenge.document_management_service_challenge.service;

import com.clara.ops.challenge.document_management_service_challenge.config.MinioProperties;
import com.clara.ops.challenge.document_management_service_challenge.domain.*;
import com.clara.ops.challenge.document_management_service_challenge.exception.*;
import com.clara.ops.challenge.document_management_service_challenge.repository.*;
import com.clara.ops.challenge.document_management_service_challenge.storage.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

  private final DocumentRepository documents;
  private final TagRepository tags;
  private final StoragePort storage;
  private final MinioProperties props;

  public DocumentService(
      DocumentRepository documents,
      TagRepository tags,
      StoragePort storage,
      MinioProperties props) {
    this.documents = documents;
    this.tags = tags;
    this.storage = storage;
    this.props = props;
  }

  public record RegisterResult(UUID id, String uploadUrl) {}

  @Transactional
  public RegisterResult register(String user, String name, List<String> tagNames) {
    UUID id = UUID.randomUUID();
    String path = user + "/" + name;

    DocumentEntity d = new DocumentEntity();
    d.setId(id);
    d.setUser(user);
    d.setName(name);
    d.setMinioPath(path);
    d.setStatus(DocumentStatus.PENDING);
    d.setTags(resolveTags(tagNames));
    documents.save(d);

    return new RegisterResult(id, storage.presignedPutUrl(path));
  }

  @Transactional
  public void complete(UUID id) {
    DocumentEntity d =
        documents
            .findById(id)
            .orElseThrow(() -> new DocumentNotFoundException("Document " + id + " not found"));
    StoredObject obj =
        storage
            .stat(d.getMinioPath())
            .orElseThrow(() -> new DocumentNotReadyException("Object not uploaded for " + id));
    if (obj.size() > props.maxFileSizeBytes()) {
      storage.remove(d.getMinioPath());
      throw new InvalidDocumentException("File exceeds " + props.maxFileSizeBytes() + " bytes");
    }
    d.setSize(obj.size());
    d.setFileType(obj.contentType());
    d.setStatus(DocumentStatus.COMPLETED);
    documents.save(d);
  }

  @Transactional(readOnly = true)
  public Page<DocumentEntity> search(
      String user, String name, List<String> tagNames, Pageable pageable) {
    Page<DocumentEntity> page =
        documents.findAll(DocumentSpecifications.filter(user, name, tagNames), pageable);
    // Initialize lazy tags while the session is open (open-in-view is false).
    // default_batch_fetch_size batches these loads to avoid an N+1.
    page.getContent().forEach(d -> d.getTags().size());
    return page;
  }

  @Transactional(readOnly = true)
  public String downloadUrl(UUID id) {
    DocumentEntity d =
        documents
            .findById(id)
            .orElseThrow(() -> new DocumentNotFoundException("Document " + id + " not found"));
    if (d.getStatus() != DocumentStatus.COMPLETED) {
      throw new DocumentNotReadyException("Document " + id + " is not ready");
    }
    return storage.presignedGetUrl(d.getMinioPath());
  }

  private Set<TagEntity> resolveTags(List<String> names) {
    if (names == null) return new HashSet<>();
    return names.stream()
        .map(n -> tags.findByName(n).orElseGet(() -> tags.save(new TagEntity(null, n))))
        .collect(Collectors.toSet());
  }
}
