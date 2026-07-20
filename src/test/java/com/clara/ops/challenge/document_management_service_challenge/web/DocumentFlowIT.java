package com.clara.ops.challenge.document_management_service_challenge.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.minio.*;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.MountableFile;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentFlowIT {

  @Container static PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15.4")
      .withDatabaseName("challenge")
      .withCopyFileToContainer(
          MountableFile.forClasspathResource("db/schema-init.sql"),
          "/docker-entrypoint-initdb.d/schema-init.sql");
  @Container static MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", PG::getJdbcUrl);
    r.add("spring.datasource.username", PG::getUsername);
    r.add("spring.datasource.password", PG::getPassword);
    r.add("minio.internal-endpoint", MINIO::getS3URL);
    r.add("minio.public-endpoint", MINIO::getS3URL);
    r.add("minio.access-key", MINIO::getUserName);
    r.add("minio.secret-key", MINIO::getPassword);
  }

  @Autowired TestRestTemplate rest;
  @LocalServerPort int port;

  @BeforeAll
  static void bucket() throws Exception {
    MinioClient c = MinioClient.builder().endpoint(MINIO.getS3URL())
        .credentials(MINIO.getUserName(), MINIO.getPassword()).build();
    c.makeBucket(MakeBucketArgs.builder().bucket("document-bucket").build());
  }

  @Test
  void registerUploadCompleteSearchDownload() throws Exception {
    // register
    ResponseEntity<Map> reg = rest.postForEntity(base() + "/upload",
        json("{\"user\":\"alice\",\"name\":\"doc1.pdf\",\"tags\":[\"invoice\"]}"), Map.class);
    assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = (String) reg.getBody().get("id");
    String putUrl = (String) reg.getBody().get("uploadUrl");

    // upload straight to MinIO
    byte[] pdf = "%PDF-1.7 body".getBytes();
    int put = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI.create(putUrl))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf)).build(),
        HttpResponse.BodyHandlers.discarding()).statusCode();
    assertThat(put).isEqualTo(200);

    // complete
    assertThat(rest.postForEntity(base() + "/upload/" + id + "/complete", null, Void.class)
        .getStatusCode()).isEqualTo(HttpStatus.OK);

    // search finds it
    ResponseEntity<Map> search = rest.postForEntity(base() + "/search",
        json("{\"user\":\"alice\"}"), Map.class);
    assertThat(((List<?>) search.getBody().get("documents"))).hasSize(1);

    // download returns a url
    ResponseEntity<Map> dl = rest.getForEntity(base() + "/download/" + id, Map.class);
    assertThat((String) dl.getBody().get("url")).contains("alice/doc1.pdf");
  }

  private String base() { return "http://localhost:" + port + "/document-management"; }

  private HttpEntity<String> json(String body) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, h);
  }
}
