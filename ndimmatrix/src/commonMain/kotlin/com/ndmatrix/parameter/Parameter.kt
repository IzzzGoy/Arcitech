package com.ndmatrix.parameter

import com.ndmatrix.parameter.CallMetadata.Companion.CallMetadataKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.safeCast
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents a reactive parameter holding state of type [S].
 *
 * Parameters expose a read-only [value] and a [flow] to observe state changes.
 *
 * @param S the type of the parameter's state.
 */
interface Parameter<S: Any?> {
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

/**
 * Execution metadata produced after handling a message.
 *
 * @param E the type of the message processed.
 * @property event the message instance that was handled.
 * @property duration the time duration taken to process the event.
 * @property parentId the UUID of the parent event in the execution tree, or null if root.
 * @property currentId the UUID assigned to this event execution.
 */
@OptIn(ExperimentalUuidApi::class)
data class PostExecMetadata<E: Message>(
    val event: E,
    val duration: Duration,
    val parentId: Uuid?,
    val currentId: Uuid,
)

/**
 * Base class for all entities capable of handling [Message] instances.
 *
 * Provides a flow of post-execution metadata for observability and debugging.
 *
 * @param E the type of [Message] this handler processes.
 */
@Suppress("UNCHECKED_CAST")
abstract class EventHandler<E: Message> {
    /**
     * Key to determinate which event must be processed.
     * */
    protected abstract val messageType: KClass<E>

    /**
     * Internal mutable flow collecting metadata after each message is handled.
     */
    protected val _postMetadata: MutableSharedFlow<PostExecMetadata<*>> = MutableSharedFlow()

    /**
     * Public shared flow emitting metadata after each message handling.
     */
    val postMetadata: SharedFlow<PostExecMetadata<*>> = _postMetadata.asSharedFlow()

    /**
     * Handles a message of type [E] with optimized logic.
     *
     * @param e the message instance to handle.
     */
    abstract suspend fun handle(e: E)

    /**
     * Generic entry point for processing any [Message].
     *
     * Used to unify handling in event chains and avoid type projection issues.
     *
     * @param e the message to process.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun process(e: Message) {
        if (messageType.isInstance(e)) {
            measureTime {
                handle(e as E)
            }.also {
                _postMetadata.emit(
                    PostExecMetadata(
                        e,
                        it,
                        coroutineContext[CallMetadataKey]!!.parentId,
                        coroutineContext[CallMetadataKey]!!.currentId
                    )
                )
            }
        }

    }
}

/**
 * Marker class for [EventHandler]s that process [Message.Intent] instances.
 *
 * Differentiates intent handlers from general event handlers.
 *
 * @param E the specific subtype of [Message.Intent] this handler processes.
 */
abstract class IntentHandler<E: Message.Intent> : EventHandler<E>()

/**
 * Base implementation of [Parameter], combining state management and intent handling.
 *
 * ParameterHolders react to intents of type [E] to update their internal state of type [S].
 *
 * @param E the intent message type used to mutate the parameter state.
 * @param S the type of the parameter's state.
 * @param initialValue the initial state value for the parameter.
 */
abstract class ParameterHolder<E: Message.Intent, S: Any?>(initialValue: S) :
    Parameter<S>,
    IntentHandler<E>() {

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