package com.clara.ops.challenge.document_management_service_challenge.support;

import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Shared base for all integration tests.
 *
 * <p>Deliberately NOT annotated with {@code @Testcontainers}: that annotation manages container
 * lifecycle per test class, which is exactly what we want to avoid here. Instead, a single Postgres
 * container and a single MinIO container are started once for the whole JVM (in a static
 * initializer) and reused by every IT class that extends this one. This keeps Docker resource usage
 * low and avoids the connection-pool exhaustion that comes from several containers competing for
 * resources simultaneously. Testcontainers' Ryuk reaper cleans both containers up when the JVM
 * exits.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  protected static final String BUCKET = "document-bucket";

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:15.4")
          .withDatabaseName("challenge")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("db/schema-init.sql"),
              "/docker-entrypoint-initdb.d/schema-init.sql");

  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

  static {
    POSTGRES.start();
    MINIO.start();
    try {
      MinioClient client =
          MinioClient.builder()
              .endpoint(MINIO.getS3URL())
              .credentials(MINIO.getUserName(), MINIO.getPassword())
              .build();
      client.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
    } catch (Exception e) {
      throw new IllegalStateException("Could not create bucket " + BUCKET, e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("minio.internal-endpoint", MINIO::getS3URL);
    r.add("minio.public-endpoint", MINIO::getS3URL);
    r.add("minio.access-key", MINIO::getUserName);
    r.add("minio.secret-key", MINIO::getPassword);
    r.add("minio.bucket", () -> BUCKET);
  }
}
