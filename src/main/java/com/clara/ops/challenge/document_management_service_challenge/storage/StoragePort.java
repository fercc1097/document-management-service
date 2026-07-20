package com.clara.ops.challenge.document_management_service_challenge.storage;

import java.util.Optional;

public interface StoragePort {
  String presignedPutUrl(String objectPath);

  String presignedGetUrl(String objectPath);

  Optional<StoredObject> stat(String objectPath);

  void remove(String objectPath);
}
