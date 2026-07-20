# Memory Measurement — 50MB Constraint

**Date:** 2026-07-20 · measured on Apple Silicon (arm64), Docker Desktop 27.3.1.

## Goal

Determine empirically whether the service can run under the challenge's **50MB
container memory limit**, before committing the runtime architecture (design doc D7).

## Setup

A minimal *walking skeleton* exercising the real stack that drives footprint:
Spring Boot 3.4.3 MVC (Tomcat) + Spring Data JPA (Hibernate) + Hikari + MinIO SDK,
connected to PostgreSQL, one trivial `/health` endpoint. The file data-path is already
**out of the service** (presigned PUT), so this measures the *resting* JVM floor.

- Base image: `eclipse-temurin:17-jre` (non-Alpine).
- `JAVA_OPTS=-XX:MaxRAMPercentage=75 -XX:+UseSerialGC` (SerialGC = lowest GC overhead).
- `UseContainerSupport` on (default): the JVM sizes the heap from the cgroup limit.

## Results

| Container memory limit |         Outcome          |           Observed usage            |
|------------------------|--------------------------|-------------------------------------|
| unlimited              | starts                   | 296.9 MiB (JVM relaxes to host RAM) |
| **50 MB**              | **OOM — does not start** | —                                   |
| **96 MB**              | **OOM — does not start** | —                                   |
| 128 MB                 | starts                   | 125.9 MiB (at the ceiling)          |
| 160 MB                 | starts                   | 159.7 MiB                           |
| 200 MB                 | starts                   | 199.7 MiB                           |
| 256 MB                 | starts                   | 241.4 MiB                           |

## Conclusion

The startup floor of this stack is **between 96 and 128 MB** — roughly **2.5× the
50MB target**. 50MB is **unreachable on the JVM**, even with the data-path removed,
SerialGC, and container-aware sizing. The cause is structural, not tuning:
class-metadata (metaspace) for the Spring/Hibernate class graph, a minimum viable
heap, JIT code cache, thread stacks, and JVM native memory together exceed 50MB before
any request is served. This is why `-Xmx50m` alone (used by most reference solutions)
is misleading: it caps only the heap; under a real 50MB cgroup the process never boots.

## Decision (design doc D7)

**Ship on a tuned JVM with a realistic container limit (~256MB) and document the
blocker with this evidence.** The 50MB limit is treated as aspirational and explained,
not faked. Rationale: the challenge explicitly values reasoning and honest reporting of
blockers over a limit met only on paper.

**Evolution path:** a GraalVM native image (Spring AOT) is the only lever that could
approach 50MB — it removes metaspace/JIT and typically lands ~40–90 MB RSS — but with
Hibernate it may still not meet 50MB strictly, and it adds significant build/AOT cost.
Documented as the production evolution if the limit becomes a hard requirement.

## Under concurrent load — full containerized stack (Task 8)

With the complete `docker-compose` stack (service + PostgreSQL + MinIO), 10 concurrent
flows (register → presigned PUT straight to MinIO → complete), all sharing a brand-new
tag, were run against the service capped at 256M:

- **10/10 flows succeeded** (`PUT 200`, `complete 200`); search by the shared tag
  returns all 10 documents. The presigned design keeps file bytes out of the service, so
  10 parallel 500MB uploads never pressure its memory.
- **Service stayed stable under the 256M limit** (peak RSS ~254 MiB, no OOM-kill). The
  peak sits near the cap because the JVM grows its heap to the available headroom
  (`MaxRAMPercentage=75`), not because it needs it — the startup floor is still ~128MB.

Three integration bugs that no unit/integration test caught surfaced only here, in the
real containerized stack, and were fixed (see `SOLUTION.md`, Step 3): a removed
`bitnami/postgresql` image, an unpinned MinIO region breaking presigned signing from
inside the container, and a `tags.name` unique-constraint race under concurrency.
