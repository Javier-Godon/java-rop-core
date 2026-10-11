# rop-core

Pure-Java railway-oriented programming primitives. Depends only on `jspecify` and `slf4j-api`.

```xml
<dependency>
    <groupId>es.bluesolution</groupId>
    <artifactId>rop-core</artifactId>
    <version>${rop.version}</version>
</dependency>
```

| Package | Types |
|---|---|
| `es.bluesolution.rop.core` | `Result<T>`, `ResultPipeline<T>`, `FailureResultDescription` (+ `ErrorCode`) |
| `es.bluesolution.rop.core.execution` | `ExecutionContext`, `NoOpExecutionContext`, `LoggingExecutionContext`, `ComposableExecutionContext`, `OutboxExecutionContext`/`OutboxAggregator`, `SagaExecutionContext`/`SagaAggregator` |

```java
Result<OrderId> placeOrder(PlaceOrderData data, ExecutionContext tx) {
    return Result.pipeline(data)                 // deferred: nothing runs until within(...)
        .flatMap(Stages::validate)
        .flatMap(d -> Stages.persist(d, ports))
        .within(tx)                              // every stage runs inside the context (e.g. a DB transaction)
        .map(PlaceOrderData::orderId);
}
```

Rules: never throw from domain code, never return `Result<Void>`, use `Result.pipeline(...)…within(ctx)`
(not `Result.success(...)…within(ctx)`). Full guide: [`docs/00_FRAMEWORK_START_HERE.md`](docs/00_FRAMEWORK_START_HERE.md).

## Installation (GitHub Packages)

Published from this repository to GitHub Packages on every push to `main` (`-SNAPSHOT`) and on
tags `v*` (releases). GitHub's Maven registry requires a token even for public packages: use a
classic PAT with `read:packages` locally, or `GITHUB_TOKEN` in GitHub Actions.

```xml
<!-- pom.xml -->
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Javier-Godon/java-rop-core</url>
        <snapshots><enabled>true</enabled></snapshots>
    </repository>
</repositories>

<!-- ~/.m2/settings.xml -->
<server>
    <id>github</id>
    <username>${env.GITHUB_ACTOR}</username>
    <password>${env.GITHUB_TOKEN}</password>
</server>
```

A copy is also published to the private Nexus (`./mvnw deploy -Pnexus`, see the
[erp-backend implementation plan, Phase 6](https://github.com/Javier-Godon/erp-backend/blob/main/docs/guides/FRAMEWORK_SPLIT_AND_FIRST_IMAGE_IMPLEMENTATION_PLAN.md)).

## Build

```bash
./mvnw verify      # tests, enforcer, sources and javadoc jars
```
