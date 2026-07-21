package com.clara.ops.challenge.document_management_service_challenge.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Verifies the Actuator health endpoint against the real stack. Because a Postgres container is up
 * (see {@link IntegrationTest}), the aggregated status genuinely exercises the datasource health
 * indicator rather than returning a hardcoded 200.
 */
class HealthEndpointIT extends IntegrationTest {

  @Autowired TestRestTemplate rest;
  @LocalServerPort int port;

  @Test
  void healthEndpointReportsUp() {
    ResponseEntity<Map> res =
        rest.getForEntity("http://localhost:" + port + "/actuator/health", Map.class);

    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(res.getBody()).containsEntry("status", "UP");
    // show-details=never: the response must not leak per-component internals.
    assertThat(res.getBody()).doesNotContainKey("components");
  }
}
