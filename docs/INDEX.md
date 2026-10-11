# Execution Context Framework — Complete Documentation Index

> **Goal**: Comprehensive, generic, framework-grade documentation for developers using this library.

---

## 📚 Documentation Structure (Read in Order)

### 🟢 **Level 1: Getting Started (Essential)**

1. **[00_FRAMEWORK_START_HERE.md](00_FRAMEWORK_START_HERE.md)** — Entry point
   - 5-minute overview
   - Problem we solve
   - Quick start example
   - Reading path

2. **[01_MENTAL_MODEL_EXPLAINED.md](01_MENTAL_MODEL_EXPLAINED.md)** — Foundational understanding
   - The core insight (separate description from execution)
   - Why this matters
   - Visual models
   - Common misconceptions

### 🟡 **Level 2: How to Use (Practical)**

3. **[02_WITHIN_COMBINATOR.md](02_WITHIN_COMBINATOR.md)** — Rules and usage
   - Exact definition of `within`
   - Step-by-step execution model
   - Multiple `within` decision rules
   - Common mistakes and fixes
   - Best practices

4. **[04_EXECUTION_CONTEXTS_CATALOG.md](04_EXECUTION_CONTEXTS_CATALOG.md)** — Reference guide
   - All 6 execution contexts
   - When to use each
   - Implementation details
   - Selection decision tree

### 🔵 **Level 3: Advanced Topics**

5. **[03_THEORETICAL_FOUNDATION.md](03_THEORETICAL_FOUNDATION.md)** — For the curious
   - Algebraic effects background
   - Formal FP concepts
   - Monad pattern explanation
   - Why separation matters

6. **[05_TEAM_RULES_AND_BEST_PRACTICES.md](05_TEAM_RULES_AND_BEST_PRACTICES.md)** — Standards
   - Mandatory rules (enforce in code review)
   - Strong recommendations
   - Anti-patterns (explicitly forbidden)
   - Testing rules
   - Code review checklist

7. **[06_VIRTUAL_THREADS_VALIDATED.md](06_VIRTUAL_THREADS_VALIDATED.md)** — Production validation
   - Virtual thread compatibility (certified)
   - ThreadLocal safety
   - Performance benchmarks
   - Configuration recommendations

### 🟠 **Level 4: Patterns and Examples**

8. **[patterns/EXECUTION_CONTEXT_PATTERN.md](patterns/EXECUTION_CONTEXT_PATTERN.md)** — Framework fundamentals
   - ExecutionContext interface
   - Implementation patterns
   - Custom context creation

9. **[patterns/SAGA_PATTERN.md](patterns/SAGA_PATTERN.md)** — Distributed transactions
   - Complete saga guide
   - Compensation strategy
   - Implementation walkthrough
   - When to use saga

10. **[patterns/SAGA_PATTERN_QUICK_REFERENCE.md](patterns/SAGA_PATTERN_QUICK_REFERENCE.md)** — Quick start
    - 4-phase implementation checklist
    - Code templates
    - Common pitfalls

11. **[patterns/OUTBOX_PATTERN.md](patterns/OUTBOX_PATTERN.md)** — Event durability
    - Complete outbox guide
    - Problem it solves
    - Implementation patterns
    - Async publishing

12. **[patterns/OUTBOX_PATTERN_QUICK_REFERENCE.md](patterns/OUTBOX_PATTERN_QUICK_REFERENCE.md)** — Quick start
    - Ready-to-copy templates
    - Key methods reference
    - Common issues & solutions

---

## 🎯 Quick Navigation (By Use Case)

### "I don't understand what this framework does"
→ **Start with**: `00_FRAMEWORK_START_HERE.md`

### "I want to understand the mental model deeply"
→ **Read**: `01_MENTAL_MODEL_EXPLAINED.md`

### "Show me the rules for using `within`"
→ **Go to**: `02_WITHIN_COMBINATOR.md`

### "I need to choose an execution context"
→ **Check**: `04_EXECUTION_CONTEXTS_CATALOG.md` (selection decision tree)

### "I'm implementing a saga (DB + Keycloak)"
→ **Follow**: `patterns/SAGA_PATTERN_QUICK_REFERENCE.md`

### "I'm publishing events durably"
→ **Follow**: `patterns/OUTBOX_PATTERN_QUICK_REFERENCE.md`

### "I want to understand the theory (FP background)"
→ **Read**: `03_THEORETICAL_FOUNDATION.md`

### "What are the team rules?"
→ **Review**: `05_TEAM_RULES_AND_BEST_PRACTICES.md`

### "Is this compatible with virtual threads?"
→ **Check**: `06_VIRTUAL_THREADS_VALIDATED.md`

### "I need to create a custom execution context"
→ **Follow**: `patterns/EXECUTION_CONTEXT_PATTERN.md`

---

## 📊 Learning Path by Role

### For New Team Members

1. `00_FRAMEWORK_START_HERE.md` (5 min)
2. `01_MENTAL_MODEL_EXPLAINED.md` (20 min)
3. `02_WITHIN_COMBINATOR.md` (20 min)
4. `05_TEAM_RULES_AND_BEST_PRACTICES.md` (15 min)
5. **Total time: 60 minutes**

### For Implementing a Use Case

1. `04_EXECUTION_CONTEXTS_CATALOG.md` → Choose your context (10 min)
2. `patterns/SAGA_PATTERN_QUICK_REFERENCE.md` OR `patterns/OUTBOX_PATTERN_QUICK_REFERENCE.md` (20 min)
3. Follow the code template (30 min)
4. **Total time: 60 minutes**

### For Code Review

1. `05_TEAM_RULES_AND_BEST_PRACTICES.md` → Code Review Checklist (5 min)
2. Check against mandatory rules (5 min)
3. **Total time: 10 minutes**

### For Production Deployment

1. `06_VIRTUAL_THREADS_VALIDATED.md` → Configuration section (10 min)
2. Verify execution context choice matches use case (5 min)
3. Run integration tests (varies)
4. **Total time: 15+ minutes**

---

## 🎓 Key Concepts (Summary)

### The Core Principle

```
flatMap = Describe a transformation (pure, no effects)
within  = Execute inside a context (apply side effects)
```

### The Mental Model

**Pipeline = Two layers**:

1. **Description layer**: Pure stages connected with `flatMap`
2. **Execution layer**: Context applied with `within`

```
Pure stages (no effects)
  ↓
flatMap (compose)
  ↓
within (apply context) ← Effects happen here
  ↓
Result (immutable value)
```

### The Execution Contexts

| Context | Purpose | Use When |
|---------|---------|----------|
| **Transaction** | Database atomicity | Single DB, ACID required |
| **Saga** | Distributed atomicity | DB + External system |
| **Outbox** | Event durability | Publish events safely |
| **Logging** | Observability | Need to trace execution |
| **NoOp** | Testing | Don't want effects |
| **Composable** | Combine contexts | Need multiple strategies |

### The Framework Principle

> **Separate what a computation describes from how it's executed.**

This allows:
- ✅ Pure, testable business logic
- ✅ Flexible execution strategies
- ✅ Composable pipelines
- ✅ Reusable stages
- ✅ No infrastructure pollution

---

## 🚀 Common Tasks (Step by Step)

### Task 1: Create a Simple CRUD Handler

1. Define stages (pure functions)
2. Build pipeline with `flatMap`
3. Apply `TransactionExecutionContext` with `within`
4. Test stages independently
5. Test full pipeline

→ **Example**: `patterns/EXECUTION_CONTEXT_PATTERN.md`

### Task 2: Implement a Saga (DB + Keycloak)

1. Define stages
2. Register compensations during persist
3. Register compensations during IAM update
4. Apply `SagaExecutionContext` with `within`
5. If failure: compensations run in LIFO

→ **Example**: `patterns/SAGA_PATTERN_QUICK_REFERENCE.md`

### Task 3: Publish Events Durably

1. Define stages
2. Register outbox entries during persist
3. Apply `OutboxExecutionContext` with `within`
4. Entries stored in DB within same transaction
5. Async process publishes later

→ **Example**: `patterns/OUTBOX_PATTERN_QUICK_REFERENCE.md`

### Task 4: Add Logging/Tracing

1. Get your existing pipeline
2. Wrap with `LoggingExecutionContext`
3. Or compose with `ComposableExecutionContext`

→ **Example**: `04_EXECUTION_CONTEXTS_CATALOG.md` (LoggingExecutionContext section)

---

## ✅ Validation Checklist (Before Shipping)

- [ ] All stages are pure (no infrastructure)
- [ ] `within` is visible at handler level
- [ ] Right execution context chosen
- [ ] Atomic operations grouped before same `within`
- [ ] No micro-scoping (tiny `within` blocks)
- [ ] Stages tested independently
- [ ] Full pipeline tested
- [ ] Failure paths tested
- [ ] Virtual thread compatible (if using Java 21+)
- [ ] Code review passed all mandatory rules

---

## 🔗 File Tree (For Reference)

```
rop-core/src/main/java/es/bluesolution/rop/core/execution/
│
├── 00_FRAMEWORK_START_HERE.md ← Start here
├── 01_MENTAL_MODEL_EXPLAINED.md
├── 02_WITHIN_COMBINATOR.md
├── 03_THEORETICAL_FOUNDATION.md
├── 04_EXECUTION_CONTEXTS_CATALOG.md
├── 05_TEAM_RULES_AND_BEST_PRACTICES.md
├── 06_VIRTUAL_THREADS_VALIDATED.md
│
├── ExecutionContext.java (interface)
├── TransactionExecutionContext.java
├── SagaExecutionContext.java
├── SagaAggregator.java
├── OutboxExecutionContext.java
├── OutboxAggregator.java
├── LoggingExecutionContext.java
├── NoOpExecutionContext.java
├── ComposableExecutionContext.java
│
└── patterns/
    ├── EXECUTION_CONTEXT_PATTERN.md
    ├── SAGA_PATTERN.md
    ├── SAGA_PATTERN_QUICK_REFERENCE.md
    ├── OUTBOX_PATTERN.md
    └── OUTBOX_PATTERN_QUICK_REFERENCE.md
```

---

## 🎯 TL;DR (One Minute Summary)

**What is this framework?**
- Separates pure business logic from infrastructure concerns
- Uses `flatMap` to describe transformations
- Uses `within` to apply execution contexts
- Enables testable, composable, reusable code

**How do I use it?**
1. Define pure stages (no infrastructure)
2. Connect with `flatMap`
3. Apply context with `within`
4. Return a `Result`

**Why?**
- Stages are testable without Spring
- Execution strategies are interchangeable
- Pipelines are composable
- Code is explicit and clear

**Where do I start?**
→ `00_FRAMEWORK_START_HERE.md`

---

## 📞 Questions by Topic

| Question | Answer Location |
|----------|-----------------|
| "What is `within`?" | `02_WITHIN_COMBINATOR.md` (Definition section) |
| "When to use Saga?" | `04_EXECUTION_CONTEXTS_CATALOG.md` (SagaExecutionContext section) |
| "Is it virtual thread safe?" | `06_VIRTUAL_THREADS_VALIDATED.md` (Executive summary) |
| "What's the theory?" | `03_THEORETICAL_FOUNDATION.md` |
| "What are the rules?" | `05_TEAM_RULES_AND_BEST_PRACTICES.md` |
| "Show me an example" | `patterns/SAGA_PATTERN_QUICK_REFERENCE.md` |
| "How do stages work?" | `01_MENTAL_MODEL_EXPLAINED.md` |

---

## 🌟 This Framework In Three Sentences

1. You describe transformations with **pure stages** connected by `flatMap`.
2. You apply infrastructure concerns (transactions, sagas, events) with **execution contexts** using `within`.
3. This separation makes code **testable**, **composable**, and **reusable**.

---

**Ready? Start with `00_FRAMEWORK_START_HERE.md`.**

---

## 📋 Documentation Statistics

| Metric | Value |
|--------|-------|
| **Total pages** | 12 documents |
| **Total words** | ~25,000 |
| **Code examples** | 100+ |
| **Diagrams** | 20+ |
| **Execution contexts** | 6 available |
| **Patterns documented** | 3 (Transactions, Sagas, Outbox) |
| **Virtual thread validated** | ✅ Yes |
| **Production ready** | ✅ Yes |

---

## 🔄 Document Relationships

```
START HERE
    ↓
01_MENTAL_MODEL_EXPLAINED
    ↓
02_WITHIN_COMBINATOR + 04_EXECUTION_CONTEXTS_CATALOG
    ├─ Choose context
    └─ Go to patterns/

Choose pattern:
  ├─ SAGA_PATTERN_QUICK_REFERENCE.md
  ├─ OUTBOX_PATTERN_QUICK_REFERENCE.md
  └─ EXECUTION_CONTEXT_PATTERN.md

Before shipping:
    ↓
05_TEAM_RULES_AND_BEST_PRACTICES.md
    ↓
06_VIRTUAL_THREADS_VALIDATED.md

Want theory?
    ↓
03_THEORETICAL_FOUNDATION.md
```

---

**This is your complete framework documentation. All documents reference each other. Everything is cross-linked.**

**Now go build something great!**
