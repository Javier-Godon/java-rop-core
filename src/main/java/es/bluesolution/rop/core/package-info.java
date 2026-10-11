/**
 * Functional ROP framework primitives.
 *
 * <p>This package owns the reusable railway-oriented/functional building blocks used
 * by consumers: {@link es.bluesolution.rop.core.Result},
 * {@link es.bluesolution.rop.core.ResultPipeline},
 * failure descriptions, and execution contexts under {@code es.bluesolution.rop.core.execution}.
 * Spring integration (transactions, HTTP response mapping) lives in {@code rop-spring}.</p>
 *
 * <p>Boundary rule: keep this package business-free. Do not add use cases,
 * aggregates, BC-specific policies, legal-ledger workflows, or orchestration here.</p>
 */
package es.bluesolution.rop.core;

