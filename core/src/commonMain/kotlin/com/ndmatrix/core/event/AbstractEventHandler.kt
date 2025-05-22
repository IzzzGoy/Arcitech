package com.ndmatrix.core.event

import com.ndmatrix.core.metadata.CallMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Abstract base class for event handlers, providing infrastructure
 * to emit child events along with metadata.
 *
 * @param E the type of input [Message.Event].
 * @param coroutineContext the coroutine context used for subscribing to output events.
 * @param messageType the key to determinate which event must be processed.
 *
 * @property events a _hot_ [kotlinx.coroutines.flow.SharedFlow] of child events without metadata.
 * @property rawEvents a [kotlinx.coroutines.flow.SharedFlow] of pairs (parentId, Message) for internal routing.
 */
@OptIn(ExperimentalUuidApi::class)
abstract class AbstractEventHandler<E : Message.Event>(
    coroutineContext: CoroutineContext,
    messageType: KClass<E>
) : PostMetadataEventHandler<E>(messageType) {
    protected val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext)
    private val _events = MutableSharedFlow<Pair<Uuid, Message>>()

    /**
     * A flow of child events without metadata.
     */
    open val events = _events
        .map { it.second }
        .shareIn(coroutineScope, SharingStarted.Companion.Eagerly)

    /**
     * A flow of child events paired with their parent ID.
     */
    val rawEvents = _events.asSharedFlow()

    /**
     * Emits a new event into the outgoing events flow.
     *
     * @param block a factory lambda that produces a [Message] instance.
     * @throws IllegalStateException if [com.ndmatrix.core.metadata.CallMetadata] is not present in the context.
     */
    @Suppress("UNUSED")
    protected suspend fun returnEvent(block: () -> Message) {
        val metadata = coroutineContext[CallMetadata.CallMetadataKey]
            ?: error("CallMetadata not found in CoroutineContext")
        val parentId = metadata.currentId

        // Launch a new coroutine for emission to avoid blocking the handler
        coroutineScope.launch {
            _events.emit(parentId to block())
        }
    }
}