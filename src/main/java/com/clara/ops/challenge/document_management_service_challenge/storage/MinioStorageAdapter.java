package com.clara.ops.challenge.document_management_service_challenge.storage;

import com.clara.ops.challenge.document_management_service_challenge.config.MinioProperties;
import com.clara.ops.challenge.document_management_service_challenge.exception.StorageException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class MinioStorageAdapter implements StoragePort {

  private final MinioClient internal;
  private final MinioClient publicClient;
  private final MinioProperties props;

  public MinioStorageAdapter(
      @Qualifier("internalMinioClient") MinioClient internal,
      @Qualifier("publicMinioClient") MinioClient publicClient,
      MinioProperties props) {
    this.internal = internal;
    this.publicClient = publicClient;
    this.props = props;
  }

  @Override
  public String presignedPutUrl(String objectPath) {
    return presign(Method.PUT, objectPath, props.presignedPutExpirySeconds());
  }

  @Override
  public String presignedGetUrl(String objectPath) {
    return presign(Method.GET, objectPath, props.presignedGetExpirySeconds());
  }

  private String presign(Method method, String objectPath, int expiry) {
    try {
      return publicClient.getPresignedObjectUrl(
          GetPresignedObjectUrlArgs.builder()
              .method(method)
              .bucket(props.bucket())
              .object(objectPath)
              .expiry(expiry, TimeUnit.SECONDS)
              .build());
    } catch (Exception e) {
      throw new StorageException("Could not presign " + method + " for " + objectPath, e);
    }
  }

  @Override
  public Optional<StoredObject> stat(String objectPath) {
    try {
      StatObjectResponse s =
          internal.statObject(
              StatObjectArgs.builder().bucket(props.bucket()).object(objectPath).build());
      return Optional.of(new StoredObject(s.size(), s.contentType()));
    } catch (ErrorResponseException e) {
      String code = e.errorResponse() != null ? e.errorResponse().code() : null;
      if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
        return Optional.empty();
      }
      throw new StorageException("Could not stat " + objectPath, e);
    } catch (Exception e) {
      throw new StorageException("Could not stat " + objectPath, e);
    }
  }

  @Override
  public void remove(String objectPath) {
    try {
      internal.removeObject(
          RemoveObjectArgs.builder().bucket(props.bucket()).object(objectPath).build());
    } catch (Exception e) {
      throw new StorageException("Could not remove " + objectPath, e);
    }
  }
}
