package com.clara.ops.challenge.document_management_service_challenge.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.config.MinioProperties;
import io.minio.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinioStorageAdapterIT {

  @Container
  static MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

  static MinioProperties props;
  static MinioStorageAdapter adapter;

  @BeforeAll
  static void setup() throws Exception {
    String ep = MINIO.getS3URL();
    props =
        new MinioProperties(
            ep,
            ep,
            MINIO.getUserName(),
            MINIO.getPassword(),
            "document-bucket",
            3600,
            3600,
            524288000L);
    MinioClient client =
        MinioClient.builder()
            .endpoint(ep)
            .credentials(MINIO.getUserName(), MINIO.getPassword())
            .build();
    client.makeBucket(MakeBucketArgs.builder().bucket("document-bucket").build());
    adapter = new MinioStorageAdapter(client, client, props);
  }

  @Test
  void presignedPutRoundTripThenStat() throws Exception {
    String path = "user1/doc1.pdf";
    String putUrl = adapter.presignedPutUrl(path);

    byte[] body = "%PDF-1.7 hello".getBytes();
    HttpResponse<Void> resp =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(putUrl))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build(),
                HttpResponse.BodyHandlers.discarding());

    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(adapter.stat(path))
        .get()
        .extracting(StoredObject::size)
        .isEqualTo((long) body.length);
    assertThat(adapter.presignedGetUrl(path)).contains(path);
  }
}
