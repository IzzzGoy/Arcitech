package com.ndmatrix.core.parameter

import com.ndmatrix.core.event.Message
import com.ndmatrix.core.event.PostMetadataIntentHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.reflect.KClass

/**
 * Base implementation of [Parameter], combining state management and intent handling.
 *
 * ParameterHolders react to intents of type [E] to update their internal state of type [S].
 *
 * @param E the intent message type used to mutate the parameter state.
 * @param S the type of the parameter's state.
 * @param initialValue the initial state value for the parameter.
 */
abstract class ParameterHolder<E : Message.Intent, S : Any?>(
    initialValue: S,
    messageType: KClass<E>
) : Parameter<S>, PostMetadataIntentHandler<E>(messageType) {

    private val _flow = MutableStateFlow(initialValue)
    override val flow: StateFlow<S> = _flow.asStateFlow()
    override val value: S get() = flow.value

    /**
     * Updates the parameter's state to the new [value].
     *
     * @param value the new state to set.
     */
    protected fun update(value: S) {
        _flow.value = value
    }
}