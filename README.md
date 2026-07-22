# Document Management Service

This backend service uploads, finds, and downloads large PDF documents. Each
document can have a size of 500 MB. The service uses a small container memory
budget. It uses Java 17, Spring Boot 3, PostgreSQL for the metadata, and MinIO
for the object storage.

The service uses presigned uploads. Each client sends the bytes directly to
MinIO. Thus the file data does not go through the service. This makes 10 parallel
uploads of 500 MB possible. The bytes do not fill the memory of the service.

But you cannot get the 50 MB target on the JVM. I measured this target. I did
not assume it. For the data and the decision, refer to
[Memory: the 50 MB constraint](#memory-the-50-mb-constraint).

- For the approach, the reasons, and the trade-offs, refer to [SOLUTION.md](SOLUTION.md).
- For the initial task, refer to [docs/CHALLENGE.md](docs/CHALLENGE.md).

## Architecture

The data flows from the Controller to the Service to the Repository. A
`StoragePort` interface hides MinIO behind the `MinioStorageAdapter`. Thus you
can change the storage backend. You do not change the service. The tags are a
normalized many-to-many relation.

The service uses two MinIO endpoints:

- An internal endpoint. The server uses it for the `statObject` and
  `removeObject` operations.
- A public endpoint. The service uses it only to sign the URLs. The external
  client can get access to these URLs.

The upload is a procedure of three steps:

1. Send `POST /upload` with the JSON metadata. The service stores the document
   as `PENDING`. The service returns a presigned PUT URL.
2. Send the PDF directly to this URL. MinIO gets the bytes.
3. Send `POST /upload/{id}/complete`. The service gets the correct size and type
   with `statObject`. The service sets the document to `COMPLETED`.

## API

The base path is `/document-management`. For the contract, refer to the
[OpenAPI spec](docs/document-management-open-api.yml).

| Method |          Path           |                            Function                            |
|--------|-------------------------|----------------------------------------------------------------|
| POST   | `/upload`               | Register the metadata. Return a presigned PUT URL.             |
| POST   | `/upload/{id}/complete` | Confirm the upload. Set it to `COMPLETED`.                     |
| POST   | `/search`               | Filter by the user, the name, or the tags. Use pages and sort. |
| GET    | `/download/{id}`        | Return a temporary presigned GET URL.                          |
| GET    | `/actuator/health`      | Show the liveness and the readiness.                           |

The search returns only the `COMPLETED` documents. The default order is the
`created_at` in the decreasing sequence. To change the order, give a `sort`
value. A search with more than one tag uses AND logic. Each document must have
all the tags that you request.

## Memory: the 50 MB constraint

I measured this constraint. I did not assume it. A JVM with Spring Boot and
Hibernate cannot fit in 50 MB. With the default values, the JVM needs much more
memory. You can tune the JVM with SerialGC, container-aware sizes, and small
stacks. But the startup floor stays at approximately 160 MB. This is
approximately 3 times the target. The metaspace causes this floor. The tuning
does not.

Thus the service uses a practical value of 256 MB. This document shows the
problem. The next step is GraalVM native. It uses approximately 40 MB to 90 MB.

The presigned PUT keeps the bytes out of the service. Thus 10 parallel uploads
still pass 10 of 10 with 256 MB.

![The default JVM and the tuned JVM with smaller memory limits, and the load test of 10 parallel uploads](docs/assets/memory-load-test.png)

For the raw numbers, refer to [docs/memory-measurement.md](docs/memory-measurement.md).

## Setup and run

You set all the configuration with environment variables. Refer to
[docker/docker-compose.yml](docker/docker-compose.yml) and
[application.yml](src/main/resources/application.yml).

### Prerequisites

- Docker and Docker Compose. This is the only requirement for option A.
- JDK 17 and Maven (the `./mvnw` wrapper is in the repository). This is for
  option B and for the tests.

### Option A — full stack with Docker Compose (recommended)

One command builds the service and starts every dependency:

```bash
cd docker && docker-compose up --build
```

This starts PostgreSQL, MinIO, a one-shot job that creates the MinIO bucket, and
the service. The service listens on `http://localhost:8080`. MinIO listens on
`http://localhost:9000` and its console on `http://localhost:9001`.

### Option B — run the service alone

Start only the dependencies with Docker, then run the service with Maven:

```bash
cd docker && docker-compose up postgresql minio createbucket   # dependencies only
./mvnw spring-boot:run                                          # the service, from the repo root
```

### Database setup

The service uses PostgreSQL. It does not create the schema at runtime
(`spring.jpa.hibernate.ddl-auto` is `none`). The schema and the tables come from
[`docker/init-scripts/schema-init.sql`](docker/init-scripts/schema-init.sql).
Docker Compose runs this script the first time it starts the database. The script
creates the `document_schema` schema, the `documents`, `tags`, and
`document_tags` tables, and the indices.

You set the database connection with environment variables (the defaults are for
a local run):

|    Variable    |                   Default                    |       Purpose        |
|----------------|----------------------------------------------|----------------------|
| `DB_URL`       | `jdbc:postgresql://localhost:5432/challenge` | The JDBC URL         |
| `DB_USERNAME`  | `postgres`                                   | The database user    |
| `DB_PASSWORD`  | `postgres`                                   | The password         |
| `DB_POOL_SIZE` | `5`                                          | The Hikari pool size |

If you use your own PostgreSQL (not the Compose one), run the init script first,
so the schema exists.

### Exercise the flow

1. Send `POST /document-management/upload` with the `user`, the `name`, and the
   `tags`. You get an `id` and an `uploadUrl`.
2. Send the PDF bytes to the `uploadUrl`.
3. Send `POST /document-management/upload/{id}/complete`.
4. Send `POST /document-management/search`. Then send
   `GET /document-management/download/{id}`.

There is a Postman collection for this flow in
[`docs/postman/`](docs/postman/).

## Tests and code quality

```bash
./mvnw clean verify
```

This command runs the unit tests and the integration tests, and it builds the
coverage report. The integration tests use Testcontainers against a real
PostgreSQL and a real MinIO, so Docker must run.

Summary of the last run:

|   Area   |                            Result                             |
|----------|---------------------------------------------------------------|
| Tests    | **37 pass** (25 unit + 12 integration), 0 failures            |
| Coverage | **94.1% lines** (JaCoCo), 75.9% branches                      |
| Format   | Spotless (google-java-format + Markdown), 42 Java files clean |

The build fails if the format is not clean. Run `./mvnw spotless:apply` to fix
it. The full numbers, the per-package coverage, and the per-suite breakdown are
in [docs/testing-and-quality.md](docs/testing-and-quality.md).

## Notes

These are the decisions. For all the details, refer to [SOLUTION.md](SOLUTION.md).

- Memory: you cannot get the 50 MB limit on the JVM. I measured a floor of
  approximately 160 MB. Thus the service uses a practical value of 256 MB. Refer
  to [Memory: the 50 MB constraint](#memory-the-50-mb-constraint).
- The presigned procedure is different from the contract. The contract shows a
  `201` with no body. This service adds a `/complete` endpoint.
- The service uses minimal validation. It checks the metadata and a size of
  500 MB or less. The service does not get the bytes. Thus it does not examine
  the content.

## Docs

- [docs/CHALLENGE.md](docs/CHALLENGE.md) — the initial challenge brief
- [SOLUTION.md](SOLUTION.md) — the approach, the reasons, the trade-offs
- [docs/document-management-open-api.yml](docs/document-management-open-api.yml) — the API contract
- [docs/testing-and-quality.md](docs/testing-and-quality.md) — the full test results and the coverage report
- [docs/minio-local-setup.md](docs/minio-local-setup.md) — the MinIO local setup
- [docs/memory-measurement.md](docs/memory-measurement.md) — the memory measurement evidence
- [docs/postman/](docs/postman/) — the Postman collection for the end-to-end flow

