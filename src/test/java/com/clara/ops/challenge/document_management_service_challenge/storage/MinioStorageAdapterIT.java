package com.clara.ops.challenge.document_management_service_challenge.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MinioStorageAdapterIT extends IntegrationTest {

  @Autowired MinioStorageAdapter adapter;

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

  @Test
  void statOnMissingKeyReturnsEmpty() {
    assertThat(adapter.stat("nobody/missing.pdf")).isEmpty();
  }

  @Test
  void removeThenStatReturnsEmpty() throws Exception {
    String path = "user1/doc-to-remove.pdf";
    String putUrl = adapter.presignedPutUrl(path);

    byte[] body = "%PDF-1.7 remove me".getBytes();
    HttpResponse<Void> resp =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create(putUrl))
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build(),
                HttpResponse.BodyHandlers.discarding());

    assertThat(resp.statusCode()).isEqualTo(200);
    assertThat(adapter.stat(path)).isPresent();

    adapter.remove(path);

    assertThat(adapter.stat(path)).isEmpty();
  }
}
