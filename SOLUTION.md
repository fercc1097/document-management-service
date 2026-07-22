# Solution Notes

## Introduction

This document records the process that I followed to solve the challenge. I wrote
it for the reviewer. I kept it short on purpose. Each section is one step. Each
step shows the reasoning and the outcome.

## Step 1 — Malware / supply-chain check

First, I examined the repository for malicious or untrusted content. I did this
before I ran anything:

- **Build wrappers:** There is only the Maven wrapper (`mvnw`, `mvnw.cmd`,
  `.mvn/wrapper`). It is the official Apache script (`wrapperVersion=3.3.2`). It
  gets Maven 3.9.9 only from `repo.maven.apache.org`. There are no suspicious
  URLs, no `eval`, and no encoded payloads.
- **Binaries:** There are no `.jar`, `.war`, or `.class` files in the commit.
  `distributionType=only-script`. Thus there is no `maven-wrapper.jar` in the
  repository.
- **Dependencies:** The stack is the standard Spring Boot 3.4.3 stack (web,
  data-jpa, postgresql, minio, lombok, test). It comes from Maven Central. There
  are no custom or untrusted repositories.

**Result:** I found no malware and no supply-chain risk. The repository is safe
to build and run.

**Startup risks that I found later, while I built the project (these are relevant
to run the project):**

- **`bitnami/postgresql:15.4.0`** — `docker/docker-compose.yml` refers to it — is
  **no longer available on Docker Hub** (Bitnami changed or removed the catalog
  tags). Thus `docker-compose up` fails as-is. I fixed this: I pinned an available
  image (I used the official `postgres:15.4` for the measurement spike).
- **`eclipse-temurin:17-*-alpine`** does not pull on Apple Silicon
  (`no match for platform in manifest`). I used the non-Alpine
  `eclipse-temurin:17-jdk` and `17-jre` variants instead.

## Step 2 — Architecture & key decisions

The full design is in
[`docs/superpowers/specs/2026-07-20-document-management-service-design.md`](docs/superpowers/specs/2026-07-20-document-management-service-design.md).
Below is the reasoning with its trade-offs.

**The design driver:** a Spring Boot + Hibernate JVM rarely goes below ~80–150MB
RSS at rest. Thus you cannot get the 50MB container limit if the file bytes also
flow through the service. Everything below comes from one rule: *keep the bytes
out of the service*.

| # |                                                                  Decision                                                                   |                   Trade-off accepted                   |                           How it scales                            |
|---|---------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|--------------------------------------------------------------------|
| 1 | **Upload with a presigned PUT** (client → MinIO directly), not a streaming proxy                                                            | The service does not see the file content; two-step upload flow | Presigned *multipart* for files > 5GB                     |
| 2 | **No presigned multipart** (YAGNI)                                                                                                          | — (500MB is below the 5GB single-PUT ceiling)          | Add it if the size ceiling rises                                   |
| 3 | **Explicit confirmation** `POST /upload/{id}/complete` (`PENDING`→`COMPLETED`)                                                              | The client must confirm; there can be orphan rows       | Event-driven (MinIO bucket notifications) + TTL reconciliation job |
| 4 | **C-S-R + storage port** (`StoragePort` / `MinioStorageAdapter`)                                                                            | A little more indirection than a bare service          | Change MinIO for a real S3 and do not touch the service            |
| 5 | **Spring Data JPA + Specifications** for the dynamic filters                                                                                | Hibernate adds a footprint at rest                     | — (the data-path memory risk is already out)                       |
| 6 | **Normalized many-to-many tags** with indices                                                                                               | Joins instead of a denormalized array                  | Postgres `text[]` + GIN index for heavy tag search                 |
| 7 | **I measured** the 50MB limit: you cannot get it on the JVM (startup floor ~160MB). Thus the service uses a tuned JVM at a practical limit and this document records the blocker | Does not meet the 50MB limit literally | GraalVM native image (~40–90MB) as the production evolution         |
| 8 | **Minimal validation** (metadata + `statObject`; no content scan)                                                                           | No magic-byte or antivirus check                       | Add a scan if the requirements change                              |

**Contract deviations (on purpose):** the presigned flow returns the upload URL
in the `201` body and adds a `/complete` endpoint. The provided OpenAPI contract
does not have either one. The design doc records both.

**Assumption — `(user, name)` is not unique:** the challenge requires the object
path `document-bucket/{user}/{name}`. The service does not force uniqueness on
`(user, name)`. If you register the same `user` and `name` again, the service
reuses the same path. Thus the next presigned PUT overwrites the bytes of the
earlier object with no warning. (The earlier `documents` row still points at a
path, but the content of that path changed.) This is a known limitation. It comes
from the required layout. It is not a bug that I fix here — the behavior does not
change. A UUID-suffixed path or a unique `(user, name)` constraint removes it if
the layout requirement becomes flexible.

**Result:** I approved the design. The service meets the 10-concurrent-uploads
requirement *by design* (the bytes never enter the service). For the 50MB limit,
an early measurement spike
([`docs/memory-measurement.md`](docs/memory-measurement.md)) showed that **you
cannot get it on the JVM** — the startup floor of the full app is ~160MB,
approximately 3 times the target. The decision: use a tuned JVM at a practical
container limit (~256MB) and record the blocker with evidence (GraalVM native is
the evolution). This follows the challenge's own guidance: explain the blockers
and do not fake a limit that is true only on paper.

## Step 3 — Integration validation (real Docker stack)

I have unit tests and Testcontainers integration tests. In addition, I validated
the full flow against the assembled stack (`docker-compose up --build`): register
→ presigned **PUT directly to MinIO** → complete → search → download. All steps
passed. For the concurrency, **10 parallel flows shared a new tag and passed
10 of 10**. The service was stable under the 256M limit (refer to
[`docs/memory-measurement.md`](docs/memory-measurement.md)).

This work found **three integration bugs that no unit test or Testcontainers test
caught**. They show only in the real assembled stack. I fixed all three:

1. **Docker Hub removed `bitnami/postgresql:15.4.0`.** Thus the provided
   `docker-compose.yml` did not start. I fixed this: I pinned the official
   `postgres:15.4`.
2. **The MinIO region was not pinned.** Thus the signing of a presigned URL made
   a `getBucketLocation` network call. This call failed from inside the service
   container (the container cannot reach the public `localhost:9000` endpoint) →
   HTTP 500 on register. I fixed this: I pinned the region on the MinIO clients.
   Thus the signing stays fully offline.
3. **The `tags.name` unique-constraint had a race under concurrency.** 10 parallel
   registers shared a new tag. They all tried to insert it → HTTP 500. I fixed
   this: I used an atomic `INSERT ... ON CONFLICT DO NOTHING` upsert. This bug was
   a direct threat to the challenge's 10-concurrent-uploads requirement.

Takeaway: the mocked tests and the Testcontainers tests are necessary but not
sufficient. The region-signing bug and the image bug live in the wiring between
the containers. The tag race fires only under real parallelism. These bugs showed
when I drove the real stack.

## Step 4 — Post-review hardening

A high-recall code review of the branch found seven items. I fixed all of them.
The full test suite still passes (16 unit tests + 9 Testcontainers integration
tests).

| # |      Area       |                                                                                                                                                 Fix                                                                                                                                                 |
|---|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Pagination      | The service now clamps `size` to `[1, 100]` and `page` to `>= 0`. An unbounded `size` could put an arbitrarily large result set in memory — a real risk with the 50MB memory target.                                                                                                                |
| 2 | Name filter     | The service escapes the LIKE metacharacters (`%`, `_`, `\`) in the `name` filter and uses an explicit `ESCAPE '\'`. Thus a value like `a%` matches literally and does not act as a wildcard.                                                                                                        |
| 3 | Response size   | `DocumentDto.size` changed from `Integer` to `Long`. The service no longer narrows the value with `intValue()` (this would overflow to a negative number if `MAX_FILE_SIZE_BYTES` went above ~2.1GB).                                                                                               |
| 4 | Object key      | `register` now rejects the path separators and the traversal segments (`/`, `\`, `.`, `..`) in `user` and `name`. Thus the required `{user}/{name}` key cannot escape the user's prefix. The `(user, name)` overwrite behavior does not change — refer to the assumption above.                     |
| 5 | Error mapping   | The service no longer maps every `IllegalArgumentException` to `400`, which hid internal server faults as client errors. It converts the client-input errors at the edge instead: a bad download id via `@PathVariable UUID` → `400`, and an invalid sort direction → `InvalidDocumentException` → `400`. |
| 6 | Tag filter      | Refer to the decision below.                                                                                                                                                                                                                                                                       |
| 7 | Schema coupling | The native tag upsert used a hardcoded `document_schema.tags`. I replaced it with Hibernate's `{h-schema}` placeholder. Thus it follows the configured `hibernate.default_schema` and cannot diverge from the JPA-mapped queries.                                                                   |

**Decision — the tag filter is AND, not OR:** the search endpoint filters the
documents by the tags with **AND** logic. A document must have *every* requested
tag to match (for example, `tags=["invoice","2024"]` returns only the documents
that have both). The challenge brief lists Tags as a filter but does not define
the multi-tag logic. Thus this is a judgment call. For a *filter*, "narrow to the
documents that have all of these" is more precise and more expected than "any of
these" (OR). OR would widen the results that the caller did not ask for. The
service uses one correlated `EXISTS` subquery per tag. Thus the main query has no
joins and no `DISTINCT`, and it is safe to paginate. If the reviewer wants OR
logic, it is a one-line change (a single `INNER JOIN` on `tags` with
`tag.name IN (:tags)` plus `DISTINCT`).

## On the 50MB limit: how you do low-memory Java

This is a note for the reviewer, because "just run Java in 50MB" is a fair
question. **You almost never get low-memory Java when you tune Spring Boot on
HotSpot. You get it when you change the base technology.** There are two separate
worlds:

- **Real embedded (microcontrollers, KB–MB of RAM):** not HotSpot and not the
  standard ecosystem. These use specialized JVMs — **MicroEJ** (runs in tens of
  KB), **JamaicaVM** (hard real-time), and, in the past, **Java ME / CLDC**. No
  Spring, no Hibernate.
- **Lightweight server/cloud Java (this case):** you reach tens of MB with the
  levers below, from the least invasive to the most invasive:

|                      Lever                      |                             What changes                              |       Typical footprint       |                 Cost                  |
|-------------------------------------------------|-----------------------------------------------------------------------|-------------------------------|---------------------------------------|
| **JVM tuning** (what this service does)         | SerialGC, cgroup-aware sizing, `-Xss`, fewer threads                  | **~160MB floor** here         | Low; cannot cross the structural floor |
| **A different JVM: Eclipse OpenJ9**             | J9 instead of HotSpot, no code change                                 | ~40–60% less RSS than HotSpot | Another JVM, lower peak throughput    |
| **AOT: GraalVM Native Image**                   | Compiles the *same* app to a binary; no JVM, no metaspace, no JIT     | **~40–90MB**                  | Slow build + reflection hints         |
| **Native-first framework: Quarkus / Micronaut** | Resolves the dependency injection at *build time*, not with runtime reflection | **~20–50MB** native  | Rewrite the app                       |

**Why this stack is heavy:** Spring Boot + Hibernate + HotSpot is built for
throughput and developer productivity, not minimal memory. It uses reflection and
dynamic proxies. These load a large class graph → **a large metaspace** (the
dominant consumer that I measured), on top of the JIT code cache and the JVM
native memory. This is the opposite of a small footprint. This is why the tuning
stops at ~160MB.

**What to do to actually get 50MB:** compile this same app with GraalVM Native
Image (the most direct way), or migrate to Quarkus/Micronaut, or change to OpenJ9
with no code change. Complementary techniques: **jlink** (a minimal runtime with
only the used modules), **CDS/AppCDS** (shared class metadata → less metaspace
and a faster start), Epsilon/Serial GC, and `-XX:TieredStopAtLevel=1` / `-Xint`
(less code cache at the cost of speed).

**Decision recap:** you can get 50MB in Java, but not with the pre-configured
Spring Boot + HotSpot stack — to meet it you must change that stack. The service
uses a tuned JVM at a practical limit and records the blocker with measured
evidence. The reasoning is the deliverable.
