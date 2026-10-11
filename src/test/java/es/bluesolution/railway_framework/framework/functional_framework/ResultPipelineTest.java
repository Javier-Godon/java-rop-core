package es.bluesolution.railway_framework.framework.functional_framework;

import es.bluesolution.railway_framework.framework.functional_framework.execution.ExecutionContext;
import es.bluesolution.railway_framework.framework.functional_framework.execution.NoOpExecutionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static es.bluesolution.railway_framework.framework.functional_framework.FailureResultDescription.ErrorCode.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD tests for ResultPipeline — the lazy/deferred monad that fixes
 * the eager-evaluation problem with Result.within().
 *
 * <h2>The Problem Being Solved</h2>
 * In Java, {@code Result.flatMap()} evaluates eagerly. This means:
 * <pre>
 * Result.success(data)
 *     .flatMap(d -> fetchFromDB(d))  // Executes NOW, outside tx!
 *     .within(txContext);             // Transaction wraps nothing useful
 * </pre>
 *
 * <h2>The Solution</h2>
 * {@code ResultPipeline} composes Suppliers lazily. Only {@code .within()} or
 * {@code .run()} triggers evaluation:
 * <pre>
 * Result.pipeline(data)
 *     .flatMap(d -> fetchFromDB(d))  // Deferred — NOT executed yet
 *     .within(txContext);             // EVERYTHING executes here, inside tx!
 * </pre>
 */
@DisplayName("ResultPipeline — Lazy/Deferred Monad for Correct .within()")
class ResultPipelineTest {

    /** NoOp context for tests that just need to trigger execution */
    private final ExecutionContext noOp = new NoOpExecutionContext();

    // =========================================================================
    // Core: Lazy Evaluation Guarantee
    // =========================================================================

    @Nested
    @DisplayName("Lazy evaluation — flatMap does NOT execute until triggered")
    class LazyEvaluationTests {

        @Test
        @DisplayName("flatMap should NOT execute the function when composing")
        void flatMapShouldNotExecuteWhenComposing() {
            var counter = new AtomicInteger(0);

            // Act: build a pipeline with a flatMap — should NOT execute
            @SuppressWarnings("unused")
            var pipeline = ResultPipeline.start("hello")
                    .flatMap(s -> {
                        counter.incrementAndGet();
                        return Result.success(s.toUpperCase());
                    });

            // Assert: function was NOT called
            assertThat(counter.get()).isZero();
        }

        @Test
        @DisplayName("map should NOT execute the function when composing")
        void mapShouldNotExecuteWhenComposing() {
            var counter = new AtomicInteger(0);

            @SuppressWarnings("unused")
            var pipeline = ResultPipeline.start("hello")
                    .map(s -> {
                        counter.incrementAndGet();
                        return s.toUpperCase();
                    });

            assertThat(counter.get()).isZero();
        }

        @Test
        @DisplayName("Multiple flatMaps should NOT execute until run()")
        void multipleFlatMapsShouldNotExecuteUntilRun() {
            var counter = new AtomicInteger(0);

            var pipeline = ResultPipeline.start(1)
                    .flatMap(n -> {
                        counter.incrementAndGet();
                        return Result.success(n + 1);
                    })
                    .flatMap(n -> {
                        counter.incrementAndGet();
                        return Result.success(n * 10);
                    })
                    .flatMap(n -> {
                        counter.incrementAndGet();
                        return Result.success(n + 5);
                    });

            // Assert: NONE of the stages executed
            assertThat(counter.get()).isZero();

            // Now trigger execution
            var result = pipeline.run();

            assertThat(counter.get()).isEqualTo(3);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(25); // (1+1)*10+5
        }

        @Test
        @DisplayName("Contrast: Result.flatMap executes eagerly (proving the problem)")
        void resultFlatMapExecutesEagerly() {
            var counter = new AtomicInteger(0);

            // This demonstrates the PROBLEM with Result.within():
            // By the time .within() would be called, flatMap already ran
            @SuppressWarnings("deprecation")
            var ignored = Result.success("hello")
                    .flatMap(s -> {
                        counter.incrementAndGet();
                        return Result.success(s.toUpperCase());
                    });

            // The function already executed!
            assertThat(counter.get()).isEqualTo(1);
        }
    }

    // =========================================================================
    // .run() — Execute without context
    // =========================================================================

    @Nested
    @DisplayName("run() — Execute pipeline directly")
    class RunTests {

        @Test
        @DisplayName("run() should execute the full pipeline and return success")
        void runShouldExecutePipelineAndReturnSuccess() {
            var result = ResultPipeline.start("hello")
                    .flatMap(s -> Result.success(s.toUpperCase()))
                    .flatMap(s -> Result.success(s + "!"))
                    .run();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("HELLO!");
        }

        @Test
        @DisplayName("run() should short-circuit on failure")
        void runShouldShortCircuitOnFailure() {
            var reachedThirdStage = new AtomicBoolean(false);

            var result = ResultPipeline.start("hello")
                    .flatMap(s -> Result.success(s.toUpperCase()))
                    .flatMap(s -> Result.<String>failure(new FailureResultDescription(VALIDATION_ERROR, "boom")))
                    .flatMap(s -> {
                        reachedThirdStage.set(true);
                        return Result.success(s + "!");
                    })
                    .run();

            assertThat(result.isFailure()).isTrue();
            assertThat(result.failure().message()).isEqualTo("boom");
            assertThat(reachedThirdStage.get()).isFalse();
        }

        @Test
        @DisplayName("run() should return failure from start if initial computation fails")
        void runShouldReturnFailureFromStart() {
            var result = ResultPipeline.of(() ->
                            Result.<String>failure(new FailureResultDescription(NOT_FOUND, "not found")))
                    .flatMap(s -> Result.success(s + "!"))
                    .run();

            assertThat(result.isFailure()).isTrue();
            assertThat(result.failure().code()).isEqualTo(NOT_FOUND);
        }
    }

    // =========================================================================
    // .within() — Execute inside ExecutionContext (the REAL fix)
    // =========================================================================

    @Nested
    @DisplayName("within() — Execute pipeline inside ExecutionContext")
    class WithinTests {

        @Test
        @DisplayName("within() should pass the deferred computation to the context")
        void withinShouldPassDeferredComputationToContext() {
            var executedInContext = new AtomicBoolean(false);

            ExecutionContext ctx = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    executedInContext.set(true);
                    return computation.get();
                }
            };

            var result = ResultPipeline.start("hello")
                    .flatMap(s -> Result.success(s.toUpperCase()))
                    .within(ctx);

            assertThat(executedInContext.get()).isTrue();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("within() should execute ALL stages inside the context (not before)")
        void withinShouldExecuteAllStagesInsideContext() {
            // This is THE critical test — proving within() defers correctly
            var stageExecutionOrder = new java.util.ArrayList<String>();

            ExecutionContext ctx = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    stageExecutionOrder.add("CONTEXT_START");
                    var result = computation.get();
                    stageExecutionOrder.add("CONTEXT_END");
                    return result;
                }
            };

            var pipeline = ResultPipeline.start("data")
                    .flatMap(s -> {
                        stageExecutionOrder.add("STAGE_1");
                        return Result.success(s + "_fetched");
                    })
                    .flatMap(s -> {
                        stageExecutionOrder.add("STAGE_2");
                        return Result.success(s + "_validated");
                    })
                    .flatMap(s -> {
                        stageExecutionOrder.add("STAGE_3");
                        return Result.success(s + "_persisted");
                    });

            // Before within(): nothing executed
            assertThat(stageExecutionOrder).isEmpty();

            // Execute within context
            var result = pipeline.within(ctx);

            // Stages execute INSIDE the context
            assertThat(stageExecutionOrder).containsExactly(
                    "CONTEXT_START",
                    "STAGE_1",
                    "STAGE_2",
                    "STAGE_3",
                    "CONTEXT_END"
            );
            assertThat(result.value()).isEqualTo("data_fetched_validated_persisted");
        }

        @Test
        @DisplayName("Contrast: Result.within() executes stages BEFORE the context (the bug)")
        @SuppressWarnings("deprecation")
        void resultWithinExecutesStagesBeforeContext() {
            var stageExecutionOrder = new java.util.ArrayList<String>();

            ExecutionContext ctx = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    stageExecutionOrder.add("CONTEXT_START");
                    var result = computation.get();
                    stageExecutionOrder.add("CONTEXT_END");
                    return result;
                }
            };

            // This demonstrates the BUG with Result.within():
            Result.success("data")
                    .flatMap(s -> {
                        stageExecutionOrder.add("STAGE_1"); // Runs BEFORE context!
                        return Result.success(s + "_fetched");
                    })
                    .flatMap(s -> {
                        stageExecutionOrder.add("STAGE_2"); // Runs BEFORE context!
                        return Result.success(s + "_validated");
                    })
                    .within(ctx);

            // BUG: stages ran BEFORE the context started!
            assertThat(stageExecutionOrder).containsExactly(
                    "STAGE_1",      // ← Should be after CONTEXT_START!
                    "STAGE_2",      // ← Should be after CONTEXT_START!
                    "CONTEXT_START",
                    "CONTEXT_END"
            );
        }

        @Test
        @DisplayName("within() should propagate failure through context")
        void withinShouldPropagateFailureThroughContext() {
            var rollbackCalled = new AtomicBoolean(false);

            ExecutionContext ctx = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    var result = computation.get();
                    if (result.isFailure()) {
                        rollbackCalled.set(true);
                    }
                    return result;
                }
            };

            var result = ResultPipeline.start("data")
                    .flatMap(s -> Result.<String>failure(new FailureResultDescription(VALIDATION_ERROR, "invalid")))
                    .within(ctx);

            assertThat(result.isFailure()).isTrue();
            assertThat(rollbackCalled.get()).isTrue();
        }

        @Test
        @DisplayName("within() result can be chained with Result operations (pure stages outside tx)")
        void withinResultCanBeChainedWithResultOperations() {
            var result = ResultPipeline.start(42)
                    .flatMap(n -> Result.success(n * 2))          // Inside context
                    .within(noOp)                                   // Execution boundary
                    .flatMap(n -> Result.success("Result: " + n))  // Outside context (pure)
                    .map(s -> s.toUpperCase());                     // Outside context (pure)

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("RESULT: 84");
        }
    }

    // =========================================================================
    // .map() — Lazy transformation
    // =========================================================================

    @Nested
    @DisplayName("map() — Lazy value transformation")
    class MapTests {

        @Test
        @DisplayName("map should transform the value lazily")
        void mapShouldTransformValueLazily() {
            var result = ResultPipeline.start("hello")
                    .map(s -> s.toUpperCase())
                    .map(s -> s + "!")
                    .run();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("HELLO!");
        }

        @Test
        @DisplayName("map should propagate failure without applying function")
        void mapShouldPropagateFailure() {
            var mapCalled = new AtomicBoolean(false);

            var result = ResultPipeline.of(() ->
                            Result.<String>failure(new FailureResultDescription(NOT_FOUND, "gone")))
                    .map(s -> {
                        mapCalled.set(true);
                        return s.toUpperCase();
                    })
                    .run();

            assertThat(result.isFailure()).isTrue();
            assertThat(mapCalled.get()).isFalse();
        }
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    @Nested
    @DisplayName("Factory methods — start() and of()")
    class FactoryTests {

        @Test
        @DisplayName("start() should create a pipeline with a success value")
        void startShouldCreateSuccessPipeline() {
            var result = ResultPipeline.start("value").run();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("value");
        }

        @Test
        @DisplayName("start() should reject null values")
        void startShouldRejectNull() {
            assertThatThrownBy(() -> ResultPipeline.start(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("of() should create a pipeline from a Supplier")
        void ofShouldCreatePipelineFromSupplier() {
            var result = ResultPipeline.of(() -> Result.success(42)).run();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo(42);
        }

        @Test
        @DisplayName("of() should defer supplier execution")
        void ofShouldDeferSupplierExecution() {
            var counter = new AtomicInteger(0);

            var pipeline = ResultPipeline.of(() -> {
                counter.incrementAndGet();
                return Result.success("computed");
            });

            assertThat(counter.get()).isZero();

            pipeline.run();
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Result.pipeline() should be a convenient entry point")
        void resultPipelineShouldBeConvenientEntryPoint() {
            var result = Result.pipeline("hello")
                    .flatMap(s -> Result.success(s.toUpperCase()))
                    .run();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value()).isEqualTo("HELLO");
        }
    }

    // =========================================================================
    // Handler-realistic scenarios
    // =========================================================================

    @Nested
    @DisplayName("Handler patterns — realistic use case scenarios")
    class HandlerPatternTests {

        @Test
        @DisplayName("Full P3 handler pattern with within()")
        void fullP3HandlerPattern() {
            // Simulate: fetch → validate → persist → buildResult
            // Only fetch and persist are "impure" (would access DB)

            var dbCalls = new java.util.ArrayList<String>();

            ExecutionContext txContext = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    dbCalls.add("TX_BEGIN");
                    var result = computation.get();
                    dbCalls.add(result.isSuccess() ? "TX_COMMIT" : "TX_ROLLBACK");
                    return result;
                }
            };

            record Data(String name, String fetched, String validated) {}
            record MyResult(String id) {}

            var result = Result.<Data>pipeline(new Data("test", null, null))
                    // Impure: fetch from DB
                    .flatMap(d -> {
                        dbCalls.add("DB_FETCH");
                        return Result.success(new Data(d.name(), "fetched_data", d.validated()));
                    })
                    // Pure: validate
                    .flatMap(d -> {
                        if (d.fetched() == null) {
                            return Result.failure(new FailureResultDescription(VALIDATION_ERROR, "Not fetched"));
                        }
                        return Result.success(new Data(d.name(), d.fetched(), "valid"));
                    })
                    // Impure: persist
                    .flatMap(d -> {
                        dbCalls.add("DB_PERSIST");
                        return Result.success(d);
                    })
                    .within(txContext)  // ← All above runs INSIDE transaction
                    // Pure: build result (outside transaction)
                    .flatMap(d -> Result.success(new MyResult("id-123")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().id()).isEqualTo("id-123");
            assertThat(dbCalls).containsExactly(
                    "TX_BEGIN",
                    "DB_FETCH",
                    "DB_PERSIST",
                    "TX_COMMIT"
            );
        }

        @Test
        @DisplayName("Handler pattern with failure should rollback")
        void handlerPatternWithFailureShouldRollback() {
            var dbCalls = new java.util.ArrayList<String>();

            ExecutionContext txContext = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    dbCalls.add("TX_BEGIN");
                    var result = computation.get();
                    dbCalls.add(result.isSuccess() ? "TX_COMMIT" : "TX_ROLLBACK");
                    return result;
                }
            };

            var result = Result.pipeline("data")
                    .flatMap(s -> {
                        dbCalls.add("DB_FETCH");
                        return Result.success(s);
                    })
                    .flatMap(s -> Result.<String>failure(
                            new FailureResultDescription(VALIDATION_ERROR, "Business rule violated")))
                    .flatMap(s -> {
                        dbCalls.add("DB_PERSIST"); // Should NOT be reached
                        return Result.success(s);
                    })
                    .within(txContext);

            assertThat(result.isFailure()).isTrue();
            assertThat(dbCalls).containsExactly(
                    "TX_BEGIN",
                    "DB_FETCH",
                    "TX_ROLLBACK"  // ← Correct: failure triggers rollback
            );
        }
    }
}

