# Testing & Code Quality Report

This document gives the full test results and the code-quality analysis for the
service. The README has the short summary. This document has the detail.

**Command:** `./mvnw clean verify`
**Date:** 2026-07-22 · Apple Silicon (arm64), Docker Desktop.
**Result:** all 37 tests pass (0 failures, 0 errors, 0 skipped). Total time ~1 min.

## How to reproduce

```bash
./mvnw clean verify        # runs the unit tests, the integration tests, and the coverage report
./mvnw jacoco:report       # regenerates the coverage report at target/site/jacoco/index.html
./mvnw spotless:check      # checks the format (Java + Markdown)
./mvnw spotless:apply      # fixes the format
```

The integration tests (`*IT`) use Testcontainers. They start a real PostgreSQL
and a real MinIO in Docker. Thus Docker must run.

## Test results

The build separates the tests in two phases:

- **Unit tests** run in the `test` phase with the Surefire plugin.
- **Integration tests** (`*IT`) run in the `integration-test` phase with the
  Failsafe plugin. They use Testcontainers.

### Unit tests (Surefire) — 25 tests

|                        Suite                         | Tests |                               What it covers                                |
|------------------------------------------------------|-------|-----------------------------------------------------------------------------|
| `DocumentControllerTest`                             | 8     | The HTTP layer: status codes, validation, the sort parameter, error mapping |
| `DocumentServiceTest`                                | 7     | The service logic: register, complete, search, and the error cases          |
| `SortCriteriaParserTest`                             | 5     | The parse of `property[,direction]` and the invalid inputs                  |
| `DocumentMapperTest`                                 | 3     | The map from the entity to the DTO                                          |
| `MinioStorageAdapterTest`                            | 1     | The presigned-URL build                                                     |
| `DocumentManagementServiceChallengeApplicationTests` | 1     | The Spring context loads                                                    |

### Integration tests (Failsafe, Testcontainers) — 12 tests

|           Suite            | Tests |                           What it covers                           |
|----------------------------|-------|--------------------------------------------------------------------|
| `DocumentSpecificationsIT` | 4     | The dynamic filters (user, name, tags) against a real PostgreSQL   |
| `MinioStorageAdapterIT`    | 3     | The MinIO `stat`, `remove`, and the absent-object case             |
| `DocumentFlowIT`           | 2     | The end-to-end flow: register → complete → search                  |
| `ConcurrentRegisterIT`     | 1     | 10 parallel registers that share a new tag (the concurrency guard) |
| `DocumentRepositoryIT`     | 1     | The repository and the tag upsert                                  |
| `HealthEndpointIT`         | 1     | The `/actuator/health` endpoint                                    |

### Totals

|         Phase          | Tests  | Failures | Errors | Skipped |
|------------------------|--------|----------|--------|---------|
| Unit (Surefire)        | 25     | 0        | 0      | 0       |
| Integration (Failsafe) | 12     | 0        | 0      | 0       |
| **Total**              | **37** | **0**    | **0**  | **0**   |

## Coverage (JaCoCo)

JaCoCo measures the coverage during the `verify` phase. The report is at
`target/site/jacoco/index.html`.

|    Metric    | Coverage  | Covered / Total |
|--------------|-----------|-----------------|
| Lines        | **94.1%** | 208 / 221       |
| Instructions | 93.3%     | 980 / 1050      |
| Methods      | 91.9%     | 57 / 62         |
| Branches     | 75.9%     | 41 / 54         |

### Coverage by package (lines)

|            Package             | Line coverage |
|--------------------------------|---------------|
| `web`                          | 100.0%        |
| `web.dto`                      | 100.0%        |
| `config`                       | 100.0%        |
| `domain`                       | 100.0%        |
| `exception`                    | 100.0%        |
| `repository`                   | 99.2%         |
| `service`                      | 93.5%         |
| `storage`                      | 82.0%         |
| `web.error`                    | 75.0%         |
| root (application entry point) | 37.5%         |

### Why the coverage is not 100%

The coverage is 94.1% of the lines and 75.9% of the branches. It is not 100%.
This is a decision on purpose. The 13 uncovered lines and the 13 uncovered
branches are in code with a low test value. A test for this code tests a mock,
not the real logic.

The build does not force a coverage target. Here JaCoCo only reports the numbers.
It does not fail the build for the coverage.

#### The uncovered code, by class

|           Class           |         Gap         |                                                                        Reason it has no test                                                                         |
|---------------------------|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `...ChallengeApplication` | 2 lines             | The `main` method. It only calls `SpringApplication.run(...)`. A unit test does not run it.                                                                          |
| `MinioStorageAdapter`     | 6 lines, 2 branches | The defensive `catch` blocks for the rare MinIO faults (a network fault or an unexpected SDK error). A test reaches them only if it forces the MinIO client to fail. |
| `GlobalExceptionHandler`  | 3 lines             | The `storage()` handler that maps a `StorageException` to a 500. No test forces a MinIO fault through the HTTP layer.                                                |
| `DocumentSpecifications`  | 4 branches          | The null-check branches of the dynamic filters (a filter is present or a filter is absent). Not every combination runs.                                              |
| `DocumentService`         | 6 branches          | The guard branches of the validations. Not every true/false combination runs.                                                                                        |
| `DocumentEntity`          | 1 branch            | The `equals` / `hashCode` that Lombok generates.                                                                                                                     |

#### Why this is acceptable

- The business logic and the HTTP layer have full coverage (`web` 100%,
  `repository` 99.2%, `service` 93.5%). The gaps are defensive plumbing, not
  logic.
- The branch coverage (75.9%) is lower than the line coverage. The reason is
  these defensive branches.
- A jump to ~100% needs artificial mocks (a MinIO client that throws) or an
  exclude for the `main` method. That work tests the mock more than the code.
  Thus I do not chase 100%.

#### How to raise it later, if it becomes a requirement

- Add an `<excludes>` for the entry point (`...ChallengeApplication`) in the
  JaCoCo plugin, so the report does not count the `main` method.
- Add 2–3 tests that mock the `MinioClient` to throw. These cover the `catch`
  blocks in `MinioStorageAdapter` and the `storage()` handler.

## Code quality (Spotless)

Spotless is the code-quality gate in the build. It runs as a `check` in every
`verify`. Thus the build fails if the code does not follow the format.

|   Area   |                                                       Rule                                                        |                 Result                 |
|----------|-------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| Java     | google-java-format (GOOGLE style), remove unused imports, format annotations, reflow long strings, format Javadoc | 42 files, all clean                    |
| Markdown | flexmark (excludes `docs/superpowers/**` and `target/**`)                                                         | all files clean after `spotless:apply` |

To fix a format violation, run `./mvnw spotless:apply`.

## Tooling summary

|           Tool            |                Purpose                |                 Where                  |
|---------------------------|---------------------------------------|----------------------------------------|
| Surefire                  | Unit tests                            | `test` phase                           |
| Failsafe + Testcontainers | Integration tests                     | `integration-test` phase               |
| JaCoCo 0.8.12             | Coverage                              | `verify` phase → `target/site/jacoco/` |
| Spotless 2.43.0           | Format / style gate (Java + Markdown) | every `verify`                         |

## Possible next step

The build has no static-analysis tool for bugs and code smells (SpotBugs, PMD,
or Checkstyle). This is a possible improvement. A tool like SpotBugs would add a
second quality gate for the logic, not only the format.
