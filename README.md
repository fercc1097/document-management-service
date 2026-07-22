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

| Method |          Path           |                    Function                     |
|--------|-------------------------|-------------------------------------------------|
| POST   | `/upload`               | Register the metadata. Return a presigned PUT URL. |
| POST   | `/upload/{id}/complete` | Confirm the upload. Set it to `COMPLETED`.      |
| POST   | `/search`               | Filter by the user, the name, or the tags. Use pages and sort. |
| GET    | `/download/{id}`        | Return a temporary presigned GET URL.           |
| GET    | `/actuator/health`      | Show the liveness and the readiness.            |

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

## Run

You set all the configuration with environment variables. Refer to
[docker/docker-compose.yml](docker/docker-compose.yml) and
[application.yml](src/main/resources/application.yml).

```bash
cd docker && docker-compose up --build
```

To do the procedure:

1. Send `POST /document-management/upload` with the `user`, the `name`, and the
   `tags`. You get an `id` and an `uploadUrl`.
2. Send the PDF bytes to the `uploadUrl`.
3. Send `POST /document-management/upload/{id}/complete`.
4. Send `POST /document-management/search`. Then send
   `GET /document-management/download/{id}`.

## Tests

```bash
./mvnw clean verify
```

This command runs the unit tests. It also runs the Testcontainers integration
tests against a real PostgreSQL and a real MinIO. For the coverage, run
`./mvnw jacoco:report`. For the format, run `./mvnw spotless:apply`.

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
- [docs/minio-local-setup.md](docs/minio-local-setup.md) — the MinIO local setup
- [docs/memory-measurement.md](docs/memory-measurement.md) — the memory measurement evidence
