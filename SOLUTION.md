# Solution Notes

## Introduction

This document records the process I followed to solve the challenge. It is written
for the reviewer and is intentionally kept short: each section is one step, with the
reasoning behind it and the outcome.

## Step 1 — Malware / supply-chain check

Before running anything, I audited the repository for malicious or untrusted content:

- **Build wrappers:** Maven wrapper only (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`). It is the
  official Apache script (`wrapperVersion=3.3.2`) and fetches Maven 3.9.9 exclusively
  from `repo.maven.apache.org`. No suspicious URLs, `eval`, or encoded payloads.
- **Binaries:** No `.jar`, `.war`, or `.class` files committed. `distributionType=only-script`,
  so no `maven-wrapper.jar` is bundled.
- **Dependencies:** Standard Spring Boot 3.4.3 stack (web, data-jpa, postgresql, minio,
  lombok, test) pulled from Maven Central. No custom/untrusted repositories.

**Result:** No malware or supply-chain risk found. Safe to build and run.

**Startup risks found later, while building (relevant to actually running the project):**

- **`bitnami/postgresql:15.4.0`** — referenced by `docker/docker-compose.yml` — is **no
  longer available on Docker Hub** (Bitnami reorganized/removed catalog tags), so
  `docker-compose up` fails as-is. Fixed by pinning an available image (the official
  `postgres:15.4` was used for the measurement spike).
- **`eclipse-temurin:17-*-alpine`** fails to pull on Apple Silicon
  (`no match for platform in manifest`); the non-Alpine `eclipse-temurin:17-jdk` /
  `17-jre` variants are used instead.

## Step 2 — Architecture & key decisions

The full design is in
[`docs/superpowers/specs/2026-07-20-document-management-service-design.md`](docs/superpowers/specs/2026-07-20-document-management-service-design.md).
Below is the reasoning distilled to its trade-offs.

**The design driver:** a Spring Boot + Hibernate JVM rarely drops below ~80–150MB RSS
at rest, so the 50MB container limit is unreachable if file bytes also flow through the
service. Everything below follows from *keeping the bytes out of the service*.

| # |                                                                  Decision                                                                   |                   Trade-off accepted                   |                           How it scales                            |
|---|---------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|--------------------------------------------------------------------|
| 1 | **Upload via presigned PUT** (client → MinIO directly), not a streaming proxy                                                               | Service is blind to file content; two-step upload flow | Presigned *multipart* for files > 5GB                              |
| 2 | **Presigned multipart rejected** (YAGNI)                                                                                                    | — (500MB < 5GB single-PUT ceiling)                     | Adopt if the size ceiling rises                                    |
| 3 | **Explicit confirmation** `POST /upload/{id}/complete` (`PENDING`→`COMPLETED`)                                                              | Depends on the client confirming; possible orphan rows | Event-driven (MinIO bucket notifications) + TTL reconciliation job |
| 4 | **C-S-R + storage port** (`StoragePort` / `MinioStorageAdapter`)                                                                            | Slightly more indirection than a bare service          | Swap MinIO for real S3 without touching the service                |
| 5 | **Spring Data JPA + Specifications** for dynamic filters                                                                                    | Hibernate adds resting footprint                       | — (data-path memory risk already removed)                          |
| 6 | **Normalized many-to-many tags** with indices                                                                                               | Joins vs a denormalized array                          | Postgres `text[]` + GIN index for heavy tag search                 |
| 7 | **Measured** the 50MB limit: unreachable on the JVM (startup floor ~128MB), so ship tuned JVM at a realistic limit and document the blocker | Does not meet the 50MB limit literally                 | GraalVM native image (~40–90MB) as the production evolution        |
| 8 | **Minimal validation** (metadata + `statObject`; no content scan)                                                                           | No magic-byte / antivirus inspection                   | Add scanning if requirements change                                |

**Contract deviations (conscious):** the presigned flow returns the upload URL in the
`201` body and adds a `/complete` endpoint — both absent from the provided OpenAPI
contract, documented in the design doc.

**Assumption — `(user, name)` is not unique:** the challenge mandates the object path
`document-bucket/{user}/{name}`, and the service does not enforce uniqueness on
`(user, name)`. Re-registering the same `user` + `name` reuses that same path, so a
subsequent presigned PUT silently overwrites the previously uploaded object's bytes
(the earlier `documents` row stays pointed at a path whose content has changed). This is
a known, documented limitation inherent to the mandated layout, not a bug being fixed
here — behavior is unchanged. A UUID-suffixed path or a unique `(user, name)` constraint
would remove it if that layout requirement were relaxed.

**Result:** Design approved. The 10-concurrent-uploads requirement is met *by design*
(bytes never enter the service). For the 50MB limit, an early measurement spike
([`docs/memory-measurement.md`](docs/memory-measurement.md)) proved it is **unreachable
on the JVM** — the stack's startup floor is ~96–128MB, ~2.5× the target. Decision:
ship on a tuned JVM at a realistic container limit (~256MB) and document the blocker
with evidence (GraalVM native noted as the evolution). This follows the challenge's own
guidance to explain blockers rather than fake a limit met only on paper.

## Step 3 — Integration validation (real Docker stack)

Beyond unit and Testcontainers integration tests, I validated the full flow against the
assembled stack (`docker-compose up --build`): register → presigned **PUT straight to
MinIO** → complete → search → download, all green. Concurrency: **10 parallel flows
sharing a new tag succeeded 10/10**, with the service stable under the 256M limit (see
[`docs/memory-measurement.md`](docs/memory-measurement.md)).

Doing this surfaced **three integration bugs that no unit or Testcontainers test caught**
— they only appear in the real assembled stack — and all were fixed:

1. **`bitnami/postgresql:15.4.0` was removed from Docker Hub**, so the provided
   `docker-compose.yml` failed to start. Fixed by pinning the official `postgres:15.4`.
2. **MinIO region was not pinned**, so signing a presigned URL triggered a
   `getBucketLocation` network call that failed from inside the service container (the
   public `localhost:9000` endpoint is unreachable there) → HTTP 500 on register. Fixed
   by pinning the region on the MinIO clients, keeping signing fully offline.
3. **`tags.name` unique-constraint race** under concurrency: 10 parallel registers
   sharing a brand-new tag all tried to insert it → HTTP 500. Fixed with an atomic
   `INSERT ... ON CONFLICT DO NOTHING` upsert. This one directly threatened the
   challenge's 10-concurrent-uploads requirement.

Takeaway: mocked and Testcontainers tests are necessary but not sufficient — the
region-signing and image bugs live in the wiring between containers, and the tag race
only fires under real parallelism. Driving the real stack is where they showed up.

## On the 50MB limit: how low-memory Java is actually done

Reviewer-facing note, since "just run Java in 50MB" is a fair question to raise.
**Low-memory Java is almost never achieved by tuning Spring Boot on HotSpot — it comes
from changing the base technology.** There are two distinct worlds:

- **Real embedded (microcontrollers, KB–MB of RAM):** not HotSpot and not the standard
  ecosystem. Specialized JVMs — **MicroEJ** (runs in tens of KB), **JamaicaVM** (hard
  real-time), historically **Java ME / CLDC**. No Spring, no Hibernate.
- **Lightweight server/cloud Java (our case):** tens of MB are reached with the
  following levers, from least to most invasive:

|                      Lever                      |                             What changes                              |       Typical footprint       |                 Cost                  |
|-------------------------------------------------|-----------------------------------------------------------------------|-------------------------------|---------------------------------------|
| **JVM tuning** (what we did)                    | SerialGC, cgroup-aware sizing, `-Xss`, fewer threads                  | **~128MB floor** here         | Low; can't cross the structural floor |
| **Different JVM: Eclipse OpenJ9**               | J9 instead of HotSpot, no code change                                 | ~40–60% less RSS than HotSpot | Another JVM, lower peak throughput    |
| **AOT: GraalVM Native Image**                   | Compiles the *same* app to a binary; no JVM, no metaspace, no JIT     | **~40–90MB**                  | Slow build + reflection hints         |
| **Native-first framework: Quarkus / Micronaut** | Dependency injection resolved at *build time*, not runtime reflection | **~20–50MB** native           | Rewrite the app                       |

**Why this stack is heavy:** Spring Boot + Hibernate + HotSpot is built for throughput
and developer productivity, not minimal memory. It leans on reflection and dynamic
proxies, which load a large class graph → **large metaspace** (the dominant consumer we
measured), on top of JIT code cache and JVM native memory. That is the opposite of a
small footprint, which is why tuning bottoms out at ~128MB.

**What we'd do to actually hit 50MB:** compile this same app with GraalVM Native Image
(most direct), or migrate to Quarkus/Micronaut, or switch to OpenJ9 without code
changes. Complementary techniques: **jlink** (minimal runtime with only used modules),
**CDS/AppCDS** (shared class metadata → less metaspace and faster start), Epsilon/Serial
GC, `-XX:TieredStopAtLevel=1` / `-Xint` (less code cache at the cost of speed).

**Decision recap:** 50MB is achievable in Java, but not with the pre-configured Spring
Boot + HotSpot stack — meeting it requires changing that stack. We ship a tuned JVM at a
realistic limit and document the blocker with measured evidence, treating reasoning as
the deliverable.
