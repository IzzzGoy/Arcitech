package com.ndmatrix.core.parameter

import kotlinx.coroutines.flow.StateFlow

/**
 * Represents a reactive parameter holding state of type [S].
 *
 * Parameters expose a read-only [value] and a [flow] to observe state changes.
 *
 * @param S the type of the parameter's state.
 */
interface Parameter<S : Any?> {
    /**
     * Current value of the parameter.
     */
    val value: S
        get() = flow.value

    /**
     * StateFlow emitting current and future values of the parameter.
     */
    val flow: StateFlow<S>
}