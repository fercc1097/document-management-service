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

| Container memory limit | Outcome | Observed usage |
|---|---|---|
| unlimited | starts | 296.9 MiB (JVM relaxes to host RAM) |
| **50 MB** | **OOM — does not start** | — |
| **96 MB** | **OOM — does not start** | — |
| 128 MB | starts | 125.9 MiB (at the ceiling) |
| 160 MB | starts | 159.7 MiB |
| 200 MB | starts | 199.7 MiB |
| 256 MB | starts | 241.4 MiB |

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
