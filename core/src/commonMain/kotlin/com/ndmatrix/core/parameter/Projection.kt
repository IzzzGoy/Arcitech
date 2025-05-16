package com.ndmatrix.core.parameter

/**
 * Base marker class for aggregate projections in the framework.
 *
 * A Projection represents a derived state of type [S], calculated
 * by applying aggregation or transformation logic over one or more
 * source parameters and/or other projections.
 *
 * Projections expose a read-only [Parameter.value] and a [Parameter.flow] of updates
 * from the underlying sources. When any source parameter or projection
 * changes, the projection recomputes its state accordingly.
 *
 * @param S the type of the projection's computed state.
 * @see Parameter
 */
@Suppress("UNUSED")
abstract class Projection<S: Any?> : Parameter<S>