package com.ndmatrix.parameter

/**
 * Base marker class for aggregate projections in the framework.
 *
 * A Projection represents a derived state of type [S], calculated
 * by applying aggregation or transformation logic over one or more
 * source parameters and/or other projections.
 *
 * Projections expose a read-only [value] and a [flow] of updates
 * from the underlying sources. When any source parameter or projection
 * changes, the projection recomputes its state accordingly.
 *
 * @param S the type of the projection's computed state.
 * @see Parameter
 */
abstract class Projection<S: Any> : Parameter<S>