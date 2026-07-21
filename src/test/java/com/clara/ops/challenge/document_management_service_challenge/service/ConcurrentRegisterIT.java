package com.clara.ops.challenge.document_management_service_challenge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.clara.ops.challenge.document_management_service_challenge.repository.DocumentRepository;
import com.clara.ops.challenge.document_management_service_challenge.repository.TagRepository;
import com.clara.ops.challenge.document_management_service_challenge.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Regression test for the {@code tags.name} unique-constraint race fixed by {@link
 * TagRepository#insertIfAbsent}: several concurrent {@code register} calls sharing a brand-new tag
 * must not fail with a {@code DataIntegrityViolationException}, and the shared tag must end up
 * persisted exactly once.
 */
class ConcurrentRegisterIT extends IntegrationTest {

  private static final int CONCURRENCY = 10;

  @Autowired DocumentService service;
  @Autowired DocumentRepository documentRepository;
  @Autowired TagRepository tagRepository;

  @BeforeEach
  void cleanState() {
    documentRepository.deleteAll();
  }

  @Test
  void concurrentRegistersSharingANewTagAllSucceed() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
    CountDownLatch ready = new CountDownLatch(CONCURRENCY);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(CONCURRENCY);
    List<Throwable> failures = new CopyOnWriteArrayList<>();
    List<DocumentService.RegisterResult> results = new CopyOnWriteArrayList<>();

    try {
      for (int i = 0; i < CONCURRENCY; i++) {
        int idx = i;
        pool.submit(
            () -> {
              try {
                ready.countDown();
                start.await();
                DocumentService.RegisterResult r =
                    service.register("user" + idx, "f" + idx + ".pdf", List.of("shared-new-tag"));
                results.add(r);
              } catch (Throwable t) {
                failures.add(t);
              } finally {
                done.countDown();
              }
            });
      }

      // Wait until every worker is parked at the latch, then release them together.
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdown();
      pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    assertThat(new ArrayList<>(failures)).isEmpty();
    assertThat(results).hasSize(CONCURRENCY);
    assertThat(documentRepository.count()).isEqualTo(CONCURRENCY);

    assertThat(tagRepository.findByName("shared-new-tag")).isPresent();
    long tagCount =
        tagRepository.findAll().stream().filter(t -> "shared-new-tag".equals(t.getName())).count();
    assertThat(tagCount).isEqualTo(1);
  }
}
