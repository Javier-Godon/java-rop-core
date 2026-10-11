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
