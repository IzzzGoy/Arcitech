package com.ndmatrix.core.event

import com.ndmatrix.core.metadata.CallMetadata
import com.ndmatrix.core.metadata.PostExecMetadata
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.safeCast
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.uuid.ExperimentalUuidApi

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
        val callMetadata = coroutineContext[CallMetadata.CallMetadataKey]
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