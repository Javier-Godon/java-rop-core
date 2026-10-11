package es.bluesolution.railway_framework.framework.functional_framework;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static es.bluesolution.railway_framework.framework.functional_framework.FailureResultDescription.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Result utility methods added for framework improvement.
 * These utilities eliminate boilerplate in repository and validation code.
 *
 * Methods tested:
 * - fromComputation(): Exception wrapper for repository methods
 * - fromNullable(): Null-to-Result conversion
 * - ensure(): Conditional validation (static and instance)
 * - combine(): Multi-result aggregation
 */
@DisplayName("Result Utilities - Framework Enhancement Tests")
class ResultUtilitiesTest {

    // =========================================================================
    // fromComputation() Tests
    // =========================================================================

    @Nested
    @DisplayName("fromComputation() - Exception Wrapper")
    class FromComputationTests {

        @Test
        @DisplayName("Should return success when computation succeeds")
        void shouldReturnSuccessWhenComputationSucceeds() {
            // Act
            Result<String> result = Result.fromComputation(
                    () -> "Hello",
                    DATABASE_ERROR,
                    "Computation failed"
            );

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("Hello", result.value());
        }

        @Test
        @DisplayName("Should return success with complex object")
        void shouldReturnSuccessWithComplexObject() {
            // Arrange
            record TestData(int id, String name) {}

            // Act
            Result<TestData> result = Result.fromComputation(
                    () -> new TestData(1, "Test"),
                    DATABASE_ERROR,
                    "Failed to create"
            );

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(1, result.value().id());
            assertEquals("Test", result.value().name());
        }

        @Test
        @DisplayName("Should return failure when computation throws RuntimeException")
        void shouldReturnFailureWhenComputationThrowsRuntimeException() {
            // Act
            Result<String> result = Result.fromComputation(
                    () -> { throw new RuntimeException("Database down"); },
                    DATABASE_ERROR,
                    "Database operation failed"
            );

            // Assert
            assertTrue(result.isFailure());
            assertEquals(DATABASE_ERROR, result.failure().code());
            assertEquals("Database operation failed", result.failure().message());
            assertNotNull(result.failure().exception());
            assertEquals("Database down", result.failure().exception().getMessage());
        }

        @Test
        @DisplayName("Should return failure when computation throws checked exception")
        void shouldReturnFailureWhenComputationThrowsCheckedException() {
            // Act
            Result<String> result = Result.fromComputation(
                    () -> { throw new IllegalStateException("Invalid state"); },
                    TECHNICAL_ERROR,
                    "Technical failure"
            );

            // Assert
            assertTrue(result.isFailure());
            assertEquals(TECHNICAL_ERROR, result.failure().code());
            assertInstanceOf(IllegalStateException.class, result.failure().exception());
        }

        @Test
        @DisplayName("Should preserve custom error code")
        void shouldPreserveCustomErrorCode() {
            // Act
            Result<String> result = Result.fromComputation(
                    () -> { throw new RuntimeException(); },
                    EXTERNAL_SERVICE_ERROR,
                    "External API failed"
            );

            // Assert
            assertEquals(EXTERNAL_SERVICE_ERROR, result.failure().code());
        }

        @Test
        @DisplayName("Should handle NullPointerException in computation")
        void shouldHandleNullPointerExceptionInComputation() {
            // Arrange
            String nullString = null;

            // Act
            Result<Integer> result = Result.fromComputation(
                    () -> nullString.length(),  // Will throw NPE
                    VALIDATION_ERROR,
                    "Null value encountered"
            );

            // Assert
            assertTrue(result.isFailure());
            assertEquals(VALIDATION_ERROR, result.failure().code());
            assertInstanceOf(NullPointerException.class, result.failure().exception());
        }
    }

    // =========================================================================
    // fromNullable() Tests
    // =========================================================================

    @Nested
    @DisplayName("fromNullable() - Null Safety")
    class FromNullableTests {

        @Test
        @DisplayName("Should return success for non-null value")
        void shouldReturnSuccessForNonNullValue() {
            // Act
            Result<String> result = Result.fromNullable("value", "Value is required");

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("value", result.value());
        }

        @Test
        @DisplayName("Should return failure for null value with default VALIDATION_ERROR")
        void shouldReturnFailureForNullValueWithDefaultValidationError() {
            // Act
            Result<String> result = Result.fromNullable(null, "Name is required");

            // Assert
            assertTrue(result.isFailure());
            assertEquals(VALIDATION_ERROR, result.failure().code());
            assertEquals("Name is required", result.failure().message());
        }

        @Test
        @DisplayName("Should return failure for null value with custom error code")
        void shouldReturnFailureForNullValueWithCustomErrorCode() {
            // Act
            Result<String> result = Result.fromNullable(null, NOT_FOUND, "Entity not found");

            // Assert
            assertTrue(result.isFailure());
            assertEquals(NOT_FOUND, result.failure().code());
            assertEquals("Entity not found", result.failure().message());
        }

        @Test
        @DisplayName("Should handle empty string as success (not null)")
        void shouldHandleEmptyStringAsSuccess() {
            // Act
            Result<String> result = Result.fromNullable("", "Value is required");

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("", result.value());
        }

        @Test
        @DisplayName("Should work with complex objects")
        void shouldWorkWithComplexObjects() {
            // Arrange
            record Customer(String name) {}
            Customer customer = new Customer("John");

            // Act
            Result<Customer> result = Result.fromNullable(customer, "Customer is required");

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("John", result.value().name());
        }
    }

    // =========================================================================
    // ensure() Static Method Tests
    // =========================================================================

    @Nested
    @DisplayName("ensure() Static - Conditional Validation")
    class EnsureStaticTests {

        @Test
        @DisplayName("Should return success when condition is true")
        void shouldReturnSuccessWhenConditionIsTrue() {
            // Arrange
            Integer value = 10;

            // Act
            Result<Integer> result = Result.ensure(
                    value,
                    v -> v > 0,
                    new FailureResultDescription(VALIDATION_ERROR, "Must be positive")
            );

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(10, result.value());
        }

        @Test
        @DisplayName("Should return failure when condition is false")
        void shouldReturnFailureWhenConditionIsFalse() {
            // Arrange
            Integer value = -5;

            // Act
            Result<Integer> result = Result.ensure(
                    value,
                    v -> v > 0,
                    new FailureResultDescription(VALIDATION_ERROR, "Must be positive")
            );

            // Assert
            assertTrue(result.isFailure());
            assertEquals(VALIDATION_ERROR, result.failure().code());
            assertEquals("Must be positive", result.failure().message());
        }

        @Test
        @DisplayName("Should work with complex predicates")
        void shouldWorkWithComplexPredicates() {
            // Arrange
            String email = "test@example.com";

            // Act
            Result<String> result = Result.ensure(
                    email,
                    e -> e != null && e.contains("@") && e.length() > 5,
                    new FailureResultDescription(VALIDATION_ERROR, "Invalid email format")
            );

            // Assert
            assertTrue(result.isSuccess());
        }
    }

    // =========================================================================
    // ensure() Instance Method Tests
    // =========================================================================

    @Nested
    @DisplayName("ensure() Instance - Fluent Validation")
    class EnsureInstanceTests {

        @Test
        @DisplayName("Should pass through success when condition is true")
        void shouldPassThroughSuccessWhenConditionIsTrue() {
            // Act
            Result<Integer> result = Result.success(10)
                    .ensure(v -> v > 0, new FailureResultDescription(VALIDATION_ERROR, "Must be positive"));

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(10, result.value());
        }

        @Test
        @DisplayName("Should convert to failure when condition is false")
        void shouldConvertToFailureWhenConditionIsFalse() {
            // Act
            Result<Integer> result = Result.success(-5)
                    .ensure(v -> v > 0, new FailureResultDescription(VALIDATION_ERROR, "Must be positive"));

            // Assert
            assertTrue(result.isFailure());
            assertEquals("Must be positive", result.failure().message());
        }

        @Test
        @DisplayName("Should short-circuit when already a failure")
        void shouldShortCircuitWhenAlreadyAFailure() {
            // Arrange
            Result<Integer> initial = Result.failure(NOT_FOUND, "Not found", null);

            // Act - ensure should NOT be evaluated
            Result<Integer> result = initial
                    .ensure(v -> { throw new RuntimeException("Should not be called"); },
                            new FailureResultDescription(VALIDATION_ERROR, "Never reached"));

            // Assert - original failure preserved
            assertTrue(result.isFailure());
            assertEquals(NOT_FOUND, result.failure().code());
            assertEquals("Not found", result.failure().message());
        }

        @Test
        @DisplayName("Should support chained ensures")
        void shouldSupportChainedEnsures() {
            // Act
            Result<Integer> result = Result.success(50)
                    .ensure(v -> v > 0, new FailureResultDescription(VALIDATION_ERROR, "Must be positive"))
                    .ensure(v -> v < 100, new FailureResultDescription(VALIDATION_ERROR, "Must be less than 100"))
                    .ensure(v -> v % 2 == 0, new FailureResultDescription(VALIDATION_ERROR, "Must be even"));

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(50, result.value());
        }

        @Test
        @DisplayName("Should fail at first false condition in chain")
        void shouldFailAtFirstFalseConditionInChain() {
            // Act
            Result<Integer> result = Result.success(150)
                    .ensure(v -> v > 0, new FailureResultDescription(VALIDATION_ERROR, "Must be positive"))
                    .ensure(v -> v < 100, new FailureResultDescription(VALIDATION_ERROR, "Must be less than 100"))
                    .ensure(v -> v % 2 == 0, new FailureResultDescription(VALIDATION_ERROR, "Must be even"));

            // Assert - fails at second condition
            assertTrue(result.isFailure());
            assertEquals("Must be less than 100", result.failure().message());
        }
    }

    // =========================================================================
    // combine() Tests
    // =========================================================================

    @Nested
    @DisplayName("combine() - Multi-Result Aggregation")
    class CombineTests {

        @Test
        @DisplayName("Should combine two successes")
        void shouldCombineTwoSuccesses() {
            // Arrange
            Result<Integer> ra = Result.success(10);
            Result<String> rb = Result.success("Hello");

            // Act
            Result<String> result = Result.combine(ra, rb, (a, b) -> b + a);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("Hello10", result.value());
        }

        @Test
        @DisplayName("Should return first failure when first Result fails")
        void shouldReturnFirstFailureWhenFirstResultFails() {
            // Arrange
            Result<Integer> ra = Result.failure(VALIDATION_ERROR, "First failed", null);
            Result<String> rb = Result.success("Hello");

            // Act
            Result<String> result = Result.combine(ra, rb, (a, b) -> b + a);

            // Assert
            assertTrue(result.isFailure());
            assertEquals("First failed", result.failure().message());
        }

        @Test
        @DisplayName("Should return second failure when second Result fails")
        void shouldReturnSecondFailureWhenSecondResultFails() {
            // Arrange
            Result<Integer> ra = Result.success(10);
            Result<String> rb = Result.failure(NOT_FOUND, "Second failed", null);

            // Act
            Result<String> result = Result.combine(ra, rb, (a, b) -> b + a);

            // Assert
            assertTrue(result.isFailure());
            assertEquals("Second failed", result.failure().message());
        }

        @Test
        @DisplayName("Should return first failure when both Results fail")
        void shouldReturnFirstFailureWhenBothResultsFail() {
            // Arrange
            Result<Integer> ra = Result.failure(VALIDATION_ERROR, "First failed", null);
            Result<String> rb = Result.failure(NOT_FOUND, "Second failed", null);

            // Act
            Result<String> result = Result.combine(ra, rb, (a, b) -> b + a);

            // Assert
            assertTrue(result.isFailure());
            assertEquals("First failed", result.failure().message());  // First failure wins
        }

        @Test
        @DisplayName("Should combine three successes")
        void shouldCombineThreeSuccesses() {
            // Arrange
            Result<Integer> ra = Result.success(1);
            Result<Integer> rb = Result.success(2);
            Result<Integer> rc = Result.success(3);

            // Act
            Result<Integer> result = Result.combine(ra, rb, rc, (a, b, c) -> a + b + c);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(6, result.value());
        }

        @Test
        @DisplayName("Should return failure when any of three Results fails")
        void shouldReturnFailureWhenAnyOfThreeResultsFails() {
            // Arrange
            Result<Integer> ra = Result.success(1);
            Result<Integer> rb = Result.failure(BUSINESS_RULE_ERROR, "Middle failed", null);
            Result<Integer> rc = Result.success(3);

            // Act
            Result<Integer> result = Result.combine(ra, rb, rc, (a, b, c) -> a + b + c);

            // Assert
            assertTrue(result.isFailure());
            assertEquals("Middle failed", result.failure().message());
        }

        @Test
        @DisplayName("Should work with different types in combine")
        void shouldWorkWithDifferentTypesInCombine() {
            // Arrange
            record Order(String customer, int quantity, double price) {}
            Result<String> customer = Result.success("John");
            Result<Integer> quantity = Result.success(5);
            Result<Double> price = Result.success(19.99);

            // Act
            Result<Order> result = Result.combine(
                    customer, quantity, price,
                    Order::new
            );

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("John", result.value().customer());
            assertEquals(5, result.value().quantity());
            assertEquals(19.99, result.value().price());
        }
    }

    // =========================================================================
    // Integration Tests - Combined Usage
    // =========================================================================

    @Nested
    @DisplayName("Integration - Combined Usage Patterns")
    class IntegrationTests {

        @Test
        @DisplayName("Should chain fromNullable with ensure")
        void shouldChainFromNullableWithEnsure() {
            // Act
            Result<String> result = Result.fromNullable("test@example.com", "Email required")
                    .ensure(e -> e.contains("@"), new FailureResultDescription(VALIDATION_ERROR, "Invalid email"));

            // Assert
            assertTrue(result.isSuccess());
        }

        @Test
        @DisplayName("Should chain fromComputation with map and ensure")
        void shouldChainFromComputationWithMapAndEnsure() {
            // Act
            Result<Integer> result = Result.fromComputation(
                            () -> "42",
                            TECHNICAL_ERROR,
                            "Failed to get value"
                    )
                    .map(Integer::parseInt)
                    .ensure(v -> v > 0, new FailureResultDescription(VALIDATION_ERROR, "Must be positive"));

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(42, result.value());
        }

        @Test
        @DisplayName("Should combine validated results")
        void shouldCombineValidatedResults() {
            // Arrange
            Result<String> name = Result.fromNullable("John", "Name required")
                    .ensure(n -> n.length() >= 2, new FailureResultDescription(VALIDATION_ERROR, "Name too short"));

            Result<Integer> age = Result.fromNullable(25, "Age required")
                    .ensure(a -> a >= 18, new FailureResultDescription(VALIDATION_ERROR, "Must be adult"));

            record Person(String name, int age) {}

            // Act
            Result<Person> result = Result.combine(name, age, Person::new);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("John", result.value().name());
            assertEquals(25, result.value().age());
        }
    }
}
