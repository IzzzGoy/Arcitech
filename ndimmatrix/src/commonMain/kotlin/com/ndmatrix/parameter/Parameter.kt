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
data class PostExecMetadata<E : Message>(
    val event: E,
    val duration: Duration,
    val parentId: Uuid?,
    val currentId: Uuid,
)

/**
 * Base interface for all entities capable of handling [Message] instances.
 *
 * Provides a flow of post-execution metadata for observability and debugging.
 *
 * @param E the type of [Message] this handler processes.
 */
@Suppress("UNCHECKED_CAST")
interface EventHandler<E : Message> {
    /**
     * Public shared flow emitting metadata after each message handling.
     */
    val postMetadata: SharedFlow<PostExecMetadata<*>>

    /**
     * Handles a message of type [E] with optimized logic.
     *
     * @param e the message instance to handle.
     */
    suspend fun handle(e: E)
}

/**
 * Abstract base class for handling post-metadata processing of specific message types.
 *
 * @param E the type of [Message] this handler processes, constrained by the generic parameter.
 * @property messageType the specific type of message this handler is designed to process.
 */
abstract class PostMetadataEventHandler<E : Message>(
    private val messageType: KClass<E>
): EventHandler<E> {
    /**
     * Internal mutable flow collecting metadata after each message is handled.
     */
    private val _postMetadata: MutableSharedFlow<PostExecMetadata<*>> = MutableSharedFlow()
    override val postMetadata: SharedFlow<PostExecMetadata<*>> = _postMetadata.asSharedFlow()

    /**
     * Generic entry point for processing any [Message].
     *
     * Used to unify handling in event chains and avoid type projection issues.
     *
     * Prefer to use this function instead of direct call to [handle]
     *
     * @param e the message to process.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun process(e: Message) {
        messageType.safeCast(e)?.let { typedMessage ->
            val executionTime = measureTime {
                handle(typedMessage)
            }
            emitExecutionMetadata(typedMessage, executionTime)
        }
    }

    /**
     * Emits metadata about the execution of a message handling operation.
     *
     * @param message the message instance that was handled.
     * @param duration the time duration taken to handle the message.
     * @throws IllegalStateException if `CallMetadataKey` is not found in the coroutine context.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun emitExecutionMetadata(message: E, duration: Duration) {
        val callMetadata = coroutineContext[CallMetadataKey]
            ?: throw IllegalStateException("CallMetadataKey not found in coroutine context")

        _postMetadata.emit(
            PostExecMetadata(
                event = message,
                duration = duration,
                parentId = callMetadata.parentId,
                currentId = callMetadata.currentId
            )
        )
    }
}

/**
 * Marker class for [EventHandler]s that process [Message.Intent] instances.
 *
 * Differentiates intent handlers from general event handlers.
 *
 * @param E the specific subtype of [Message.Intent] this handler processes.
 */
interface IntentHandler<E : Message.Intent> : EventHandler<E>

abstract class PostMetadataIntentHandler<E : Message.Intent>(
    messageType: KClass<E>
): PostMetadataEventHandler<E>(messageType), IntentHandler<E>

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