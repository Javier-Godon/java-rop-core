package es.bluesolution.railway_framework.framework.functional_framework.execution;

import es.bluesolution.railway_framework.framework.functional_framework.FailureResultDescription;
import es.bluesolution.railway_framework.framework.functional_framework.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static es.bluesolution.railway_framework.framework.functional_framework.FailureResultDescription.ErrorCode.TECHNICAL_ERROR;
import static es.bluesolution.railway_framework.framework.functional_framework.FailureResultDescription.ErrorCode.VALIDATION_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExecutionContext contracts")
class ExecutionContextContractTest {

    record StepAck(String name) {}

    private static FailureResultDescription failure(String message) {
        return new FailureResultDescription(VALIDATION_ERROR, message);
    }

    @Nested
    @DisplayName("NoOpExecutionContext")
    class NoOpContract {

        @Test
        @DisplayName("executes the supplier exactly once and returns its success")
        void executesSupplierOnceOnSuccess() {
            var executions = new AtomicInteger();
            var context = new NoOpExecutionContext();

            var result = context.execute(() -> {
                executions.incrementAndGet();
                return Result.success(new StepAck("done"));
            });

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().name()).isEqualTo("done");
            assertThat(executions.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns failures without converting them")
        void returnsFailureUnchanged() {
            var context = new NoOpExecutionContext();

            var result = context.execute(() -> Result.<StepAck>failure(failure("invalid")));

            assertThat(result.isFailure()).isTrue();
            assertThat(result.failure().message()).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("LoggingExecutionContext")
    class LoggingContract {

        @Test
        @DisplayName("delegates successful computations exactly once")
        void delegatesSuccess() {
            var delegateCalls = new AtomicInteger();
            ExecutionContext delegate = countingDelegate(delegateCalls);
            var context = new LoggingExecutionContext(delegate);

            var result = context.execute(() -> Result.success(new StepAck("logged")));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().name()).isEqualTo("logged");
            assertThat(delegateCalls.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("delegates failed computations exactly once")
        void delegatesFailure() {
            var delegateCalls = new AtomicInteger();
            ExecutionContext delegate = countingDelegate(delegateCalls);
            var context = new LoggingExecutionContext(delegate);

            var result = context.execute(() -> Result.<StepAck>failure(failure("logged failure")));

            assertThat(result.isFailure()).isTrue();
            assertThat(result.failure().message()).isEqualTo("logged failure");
            assertThat(delegateCalls.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("ComposableExecutionContext")
    class ComposableContract {

        @Test
        @DisplayName("layers behavior around the delegated context in deterministic order")
        void composesBehaviorInOrder() {
            var order = new ArrayList<String>();
            ExecutionContext base = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    order.add("base-before");
                    var result = computation.get();
                    order.add("base-after");
                    return result;
                }
            };
            var first = new RecordingContext(base, "first", order);
            var second = new RecordingContext(first, "second", order);

            var result = Result.pipeline(new StepAck("start"))
                    .map(ack -> {
                        order.add("stage");
                        return new StepAck(ack.name() + "-done");
                    })
                    .within(second);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.value().name()).isEqualTo("start-done");
            assertThat(order).containsExactly(
                    "second-before",
                    "first-before",
                    "base-before",
                    "stage",
                    "base-after",
                    "first-after",
                    "second-after"
            );
        }

        @Test
        @DisplayName("propagates delegate failures without re-executing the computation")
        void propagatesDelegateFailureOnce() {
            var executions = new AtomicInteger();
            ExecutionContext failingDelegate = new ExecutionContext() {
                @Override
                public <T> Result<T> execute(Supplier<Result<T>> computation) {
                    executions.incrementAndGet();
                    return Result.failure(failure("delegate failed"));
                }
            };
            var context = new RecordingContext(failingDelegate, "wrapper", new ArrayList<>());

            var result = context.execute(() -> Result.success(new StepAck("unreachable")));

            assertThat(result.isFailure()).isTrue();
            assertThat(result.failure().message()).isEqualTo("delegate failed");
            assertThat(executions.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("SagaExecutionContext")
    class SagaContract {

        @Test
        @DisplayName("does not run compensations when the saga succeeds")
        void successDoesNotCompensate() {
            var compensations = new AtomicInteger();
            var context = new SagaExecutionContext();

            var result = context.execute(() -> {
                context.registerCompensation(() -> {
                    compensations.incrementAndGet();
                    return Result.success(new StepAck("undo"));
                });
                return Result.success(new StepAck("success"));
            });

            assertThat(result.isSuccess()).isTrue();
            assertThat(compensations.get()).isZero();
        }

        @Test
        @DisplayName("runs registered compensations in reverse order when the saga fails")
        void failureCompensatesInLifoOrder() {
            var order = new ArrayList<String>();
            var context = new SagaExecutionContext();

            var result = context.execute(() -> {
                context.registerCompensation(() -> {
                    order.add("undo-1");
                    return Result.success(new StepAck("undo-1"));
                });
                context.registerCompensation(() -> {
                    order.add("undo-2");
                    return Result.success(new StepAck("undo-2"));
                });
                return Result.failure(failure("saga failed"));
            });

            assertThat(result.isFailure()).isTrue();
            assertThat(result.failure().message()).isEqualTo("saga failed");
            assertThat(order).containsExactly("undo-2", "undo-1");
        }

        @Test
        @DisplayName("continues remaining compensations when one compensation fails or throws")
        void compensationFailuresDoNotStopRemainingCompensations() {
            var order = new ArrayList<String>();
            var context = new SagaExecutionContext();

            var result = context.execute(() -> {
                context.registerCompensation(() -> {
                    order.add("first-registered");
                    return Result.success(new StepAck("first"));
                });
                context.registerCompensation(() -> {
                    order.add("fails");
                    return Result.failure(new FailureResultDescription(TECHNICAL_ERROR, "compensation failed"));
                });
                context.registerCompensation(() -> {
                    order.add("throws");
                    throw new IllegalStateException("boom");
                });
                return Result.failure(failure("saga failed"));
            });

            assertThat(result.isFailure()).isTrue();
            assertThat(order).containsExactly("throws", "fails", "first-registered");
        }

        @Test
        @DisplayName("cleans ThreadLocal compensation state between executions")
        void cleansStateBetweenExecutions() {
            var order = new ArrayList<String>();
            var context = new SagaExecutionContext();

            var success = context.execute(() -> {
                context.registerCompensation(() -> {
                    order.add("stale-compensation");
                    return Result.success(new StepAck("stale"));
                });
                return Result.success(new StepAck("success"));
            });
            var failure = context.execute(() -> Result.<StepAck>failure(failure("next failure")));

            assertThat(success.isSuccess()).isTrue();
            assertThat(failure.isFailure()).isTrue();
            assertThat(order).isEmpty();
        }
    }

    @Nested
    @DisplayName("OutboxExecutionContext")
    class OutboxContract {

        @Test
        @DisplayName("retains entries after success until explicit cleanup")
        void successRetainsEntriesUntilCleanup() {
            var context = OutboxExecutionContext.withTransactions(new NoOpExecutionContext());

            var result = context.execute(() -> {
                context.registerOutboxEntry(entry("created"));
                context.registerOutboxEntry(entry("updated"));
                return Result.success(new StepAck("business success"));
            });

            assertThat(result.isSuccess()).isTrue();
            assertThat(context.getOutboxEntryCount()).isEqualTo(2);
            assertThat(context.getOutboxEntries()).hasSize(2);

            context.cleanupOutbox();

            assertThat(context.getOutboxEntryCount()).isZero();
        }

        @Test
        @DisplayName("returns a defensive copy of registered entries")
        void getOutboxEntriesReturnsDefensiveCopy() {
            var context = OutboxExecutionContext.withTransactions(new NoOpExecutionContext());

            context.execute(() -> {
                context.registerOutboxEntry(entry("created"));
                return Result.success(new StepAck("business success"));
            });

            var entries = context.getOutboxEntries();
            entries.clear();

            assertThat(context.getOutboxEntryCount()).isEqualTo(1);
            assertThat(context.getOutboxEntries()).hasSize(1);

            context.cleanupOutbox();
        }

        @Test
        @DisplayName("clears entries when business computation returns failure")
        void failureClearsEntries() {
            var context = OutboxExecutionContext.withTransactions(new NoOpExecutionContext());

            var result = context.execute(() -> {
                context.registerOutboxEntry(entry("must-not-publish"));
                return Result.failure(failure("business failure"));
            });

            assertThat(result.isFailure()).isTrue();
            assertThat(context.getOutboxEntryCount()).isZero();
            assertThat(context.getOutboxEntries()).isEmpty();
        }

        @Test
        @DisplayName("clears entries when delegate throws")
        void thrownExceptionClearsEntries() {
            var context = OutboxExecutionContext.withTransactions(new NoOpExecutionContext());

            assertThatThrownBy(() -> context.execute(() -> {
                context.registerOutboxEntry(entry("must-not-leak"));
                throw new IllegalStateException("delegate boom");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(context.getOutboxEntryCount()).isZero();
        }

        @Test
        @DisplayName("starts each execution with a clean outbox")
        void startsEachExecutionClean() {
            var context = OutboxExecutionContext.withTransactions(new NoOpExecutionContext());

            context.execute(() -> {
                context.registerOutboxEntry(entry("first"));
                return Result.success(new StepAck("first"));
            });
            assertThat(context.getOutboxEntryCount()).isEqualTo(1);

            context.execute(() -> Result.success(new StepAck("second")));

            assertThat(context.getOutboxEntryCount()).isZero();
        }

        @Test
        @DisplayName("isolates entries per thread")
        void isolatesEntriesPerThread() throws Exception {
            var context = OutboxExecutionContext.withTransactions(new NoOpExecutionContext());
            var executor = Executors.newFixedThreadPool(2);
            try {
                Callable<Integer> oneEntry = () -> executeWithEntries(context, List.of("one"));
                Callable<Integer> threeEntries = () -> executeWithEntries(context, List.of("a", "b", "c"));

                var first = executor.submit(oneEntry);
                var second = executor.submit(threeEntries);

                assertThat(first.get()).isEqualTo(1);
                assertThat(second.get()).isEqualTo(3);
                assertThat(context.getOutboxEntryCount()).isZero();
            } finally {
                executor.shutdownNow();
            }
        }

        private int executeWithEntries(OutboxExecutionContext context, List<String> eventTypes) {
            context.execute(() -> {
                eventTypes.forEach(eventType -> context.registerOutboxEntry(entry(eventType)));
                return Result.success(new StepAck("ok"));
            });
            int count = context.getOutboxEntryCount();
            context.cleanupOutbox();
            return count;
        }

        private Supplier<Result<OutboxAggregator.OutboxEntry>> entry(String eventType) {
            return () -> Result.success(new OutboxAggregator.OutboxEntry(
                    "TestAggregate",
                    "aggregate-1",
                    eventType,
                    "{}",
                    "test-topic"));
        }
    }

    private static ExecutionContext countingDelegate(AtomicInteger delegateCalls) {
        return new ExecutionContext() {
            @Override
            public <T> Result<T> execute(Supplier<Result<T>> computation) {
                delegateCalls.incrementAndGet();
                return computation.get();
            }
        };
    }

    private static final class RecordingContext extends ComposableExecutionContext {
        private final String name;
        private final List<String> order;

        private RecordingContext(ExecutionContext next, String name, List<String> order) {
            super(next);
            this.name = name;
            this.order = order;
        }

        @Override
        protected <T> Result<T> executeWithBehavior(Supplier<Result<T>> computation) {
            order.add(name + "-before");
            var result = next.execute(computation);
            order.add(name + "-after");
            return result;
        }
    }
}

