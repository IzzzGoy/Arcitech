package com.ndmatrix.parameter

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Base for all parameters.
 *
 * @property value current value of Parameter.
 * @property flow flow of parameter. May used to observe parameters flow.
 * */
interface Parameter<S: Any> {
    val value: S
        get() = flow.value
    val flow: StateFlow<S>
}

/**
 * Represents info about event execution metrics.
 *
 * @param event the event object to which the metadata pertains.
 * @param duration duration of event execution.
 * @param parentId id of parent event in execution sequence/tree.
 * @param currentId id of current event.
 * */
@OptIn(ExperimentalUuidApi::class)
data class PostExecMetadata<E: Message>(
    val event: E,
    val duration: Duration,
    val parentId: Uuid?,
    val currentId: Uuid,
)

/**
 * A base class that represents an entity that can handle events.
 *
 * @property _postMetadata inner flow with execution event metadata.
 * @property postMetadata flow with execution event metadata.
 * */
abstract class EventHandler<E: Message> {

    protected val _postMetadata: MutableSharedFlow<PostExecMetadata<*>> = MutableSharedFlow()
    val postMetadata: SharedFlow<PostExecMetadata<*>> = _postMetadata.asSharedFlow()

    /**
     * Optimized handler for [EventHandler] event type.
     * */
    abstract suspend fun handle(e: E)
    /**
     * Handler for any event type. Uses in chains to avoid problems with type projections.
     * */
    abstract suspend fun process(e: Message)

}

/**
 * Class-marker, that uses to separate [ParameterHolders] as special EventHandler.
 * */
abstract class IntentHandler<E: Message.Intent> : EventHandler<E>()

/**
 * Base implementation of [Parameter].
 *
 * @property _flow inner representation [Parameter.flow].
 * @property flow represents actual [Parameter.flow].
 * @property value represents actual [Parameter.value].
 * */
abstract class ParameterHolder<E: Message.Intent, S: Any>(initialValue: S) : Parameter<S>,
    IntentHandler<E>() {
    private val _flow = MutableStateFlow(initialValue)
    override val flow: StateFlow<S> = _flow.asStateFlow()

    override val value: S get() = flow.value

    /**
     * Method to update Parameter state.
     * */
    protected fun update(value: S) {
        _flow.value = value
    }
}