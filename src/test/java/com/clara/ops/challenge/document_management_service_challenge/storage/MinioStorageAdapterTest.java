package com.clara.ops.challenge.document_management_service_challenge.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.clara.ops.challenge.document_management_service_challenge.config.MinioProperties;
import com.clara.ops.challenge.document_management_service_challenge.exception.StorageException;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.Test;

class MinioStorageAdapterTest {

  private final MinioProperties props =
      new MinioProperties("http://internal", "http://public", "ak", "sk", "bucket", 60, 60, 1024);

  @Test
  void statSurfacesNonMissingErrorResponseAsStorageException() throws Exception {
    MinioClient internal = mock(MinioClient.class);
    MinioClient publicClient = mock(MinioClient.class);

    ErrorResponse errorResponse =
        new ErrorResponse("AccessDenied", "Access Denied.", "bucket", "obj", null, null, null);
    ErrorResponseException accessDenied = new ErrorResponseException(errorResponse, null, "req-1");
    when(internal.statObject(any(StatObjectArgs.class))).thenThrow(accessDenied);

    MinioStorageAdapter adapter = new MinioStorageAdapter(internal, publicClient, props);

    assertThatThrownBy(() -> adapter.stat("user1/doc1.pdf")).isInstanceOf(StorageException.class);
  }
}
