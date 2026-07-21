package com.clara.ops.challenge.document_management_service_challenge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class DocumentFlowIT extends IntegrationTest {

  @Autowired TestRestTemplate rest;
  @Autowired DocumentRepository documents;
  @LocalServerPort int port;

  @BeforeEach
  void cleanDocuments() {
    // Postgres is a shared singleton container across the whole IT suite (see IntegrationTest),
    // so start each test from an empty documents table: the search assertion below relies on
    // exactly one document existing for user "alice".
    documents.deleteAll();
  }

  @Test
  void registerUploadCompleteSearchDownload() throws Exception {
    // register
    ResponseEntity<Map> reg =
        rest.postForEntity(
            base() + "/upload",
            json("{\"user\":\"alice\",\"name\":\"doc1.pdf\",\"tags\":[\"invoice\"]}"),
            Map.class);
    assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String id = (String) reg.getBody().get("id");
    String putUrl = (String) reg.getBody().get("uploadUrl");

    // upload straight to MinIO
    byte[] pdf = "%PDF-1.7 body".getBytes();
    int put =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(putUrl))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(pdf))
                    .build(),
                HttpResponse.BodyHandlers.discarding())
            .statusCode();
    assertThat(put).isEqualTo(200);

    // complete
    assertThat(
            rest.postForEntity(base() + "/upload/" + id + "/complete", null, Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // search finds it
    ResponseEntity<Map> search =
        rest.postForEntity(base() + "/search", json("{\"user\":\"alice\"}"), Map.class);
    assertThat(((List<?>) search.getBody().get("documents"))).hasSize(1);

    // download returns a url
    ResponseEntity<Map> dl = rest.getForEntity(base() + "/download/" + id, Map.class);
    assertThat((String) dl.getBody().get("url")).contains("alice/doc1.pdf");
  }

  @Test
  void searchWithUnknownSortPropertyReturns400() throws Exception {
    ResponseEntity<Map> resp =
        rest.postForEntity(
            base() + "/search?sort=notaproperty,asc", json("{\"user\":\"alice\"}"), Map.class);
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody().get("status")).isEqualTo(400);
  }

  private String base() {
    return "http://localhost:" + port + "/document-management";
  }

  private HttpEntity<String> json(String body) {
    HttpHeaders h = new HttpHeaders();
    h.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, h);
  }
}
