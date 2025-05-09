@file:Suppress("UNCHECKED_CAST")

package com.ndmatrix.parameter

import com.ndmatrix.parameter.CallMetadata.Companion.CallMetadataKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Contains metadata for the current event execution, enabling tracking of
 * nested call hierarchies.
 *
 * @property parentId the UUID of the parent event, or null if this is the root event.
 * @property currentId the UUID of the current event within the execution chain.
 *
 * Stored in the [CoroutineContext] to propagate identifiers between coroutines.
 */
@OptIn(ExperimentalUuidApi::class)
class CallMetadata(
    val parentId: Uuid? = null,
    val currentId: Uuid = Uuid.random(),
) : CoroutineContext.Element {
    companion object {
        /**
         * Key for storing and retrieving [CallMetadata] from the [CoroutineContext].
         */
        val CallMetadataKey = object : CoroutineContext.Key<CallMetadata> {}
    }

    override val key: CoroutineContext.Key<*>
        /**
         * Returns the key associated with this context element.
         */
        get() = CallMetadataKey
}

/**
 * Abstract base class for event handlers, providing infrastructure
 * to emit child events along with metadata.
 *
 * @param E the type of input [Message.Event].
 * @param coroutineContext the coroutine context used for subscribing to output events.
 * @param messageType the key to determinate which event must be processed.
 *
 *
 * @property events a _hot_ [SharedFlow] of child events without metadata.
 * @property rawEvents a [SharedFlow] of pairs (parentId, Message) for internal routing.
 */
@OptIn(ExperimentalUuidApi::class)
abstract class AbstractEventHandler<E : Message.Event>(
    coroutineContext: CoroutineContext,
    messageType: KClass<E>
) : EventHandler<E>(messageType) {
    private val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext)
    private val _events = MutableSharedFlow<Pair<Uuid, Message>>()

    /**
     * A flow of child events without metadata.
     */
    val events = _events
        .map { it.second }
        .shareIn(coroutineScope, SharingStarted.Eagerly)

    /**
     * A flow of child events paired with their parent ID.
     */
    val rawEvents = _events.asSharedFlow()

    /**
     * Emits a new event into the outgoing events flow.
     *
     * @param block a factory lambda that produces a [Message] instance.
     * @throws IllegalStateException if [CallMetadata] is not present in the context.
     */
    protected suspend fun returnEvent(block: () -> Message) {
        val metadata = coroutineContext[CallMetadataKey]
            ?: error("CallMetadata not found in CoroutineContext")
        val parentId = metadata.currentId

        // Launch a new coroutine for emission to avoid blocking the handler
        coroutineScope.launch {
            _events.emit(parentId to block())
        }
    }

}

/**
 * Manages sequential or parallel execution of event handlers and parameter holders
 * as a tree or chain layout.
 *
 * @param E the type of the starting [Message.Event] that triggers the chain.
 * @param intentsHandlers a list of [ParameterHolder] instances handling [Message.Intent]s.
 * @param eventsSender a list of [AbstractEventHandler] instances generating child events.
 * @param coroutineContext the coroutine context for the entire chain execution.
 * @param isDebug flag to enable collection of [postMetadata] for debugging purposes.
 *
 * @property coroutineScope the internal [CoroutineScope] for launching chain coroutines.
 */
@OptIn(ExperimentalUuidApi::class)
abstract class EventChain<E : Message.Event>(
    private val intentsHandlers: List<ParameterHolder<*, *>>,
    private val eventsSender: List<AbstractEventHandler<*>>,
    coroutineContext: CoroutineContext,
    isDebug: Boolean
) : AutoCloseable {
    private val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext)

    /**
     * Emits a merged flow of all events collected from the registered event senders.
     *
     * This flow combines the `events` from each sender into a single unified stream,
     * allowing for centralized handling of `Message.Event` instances.
     *
     * The resulting flow includes all event types as defined by the `Message.Event` interface,
     * acting as an entry point for external consumers to interact with the event chain.
     */
    val events: Flow<Message> = merge(*eventsSender.map { it.events }.toTypedArray())

    init {
        if (isDebug) {
            startPostMetadataHandling()
        }

        startRawEventsListening()
    }

    /**
     * Hook for processing execution metadata after each event.
     * Can be overridden for logging, analytics, etc.
     *
     * @param postExecMetadata the resulting metadata of the event execution.
     */
    protected open fun postMiddleware(postExecMetadata: PostExecMetadata<*>) {}

    /**
     * Closes the chain, cancelling all running coroutines.
     */
    override fun close() {
        coroutineScope.cancel()
    }

    /**
     * Starts the event processing chain with the specified start event.
     *
     * @param e the initial event to start the chain.
     */
    fun general(e: E) {
        coroutineScope.launch(CallMetadata(null, Uuid.random())) {
            dispatchEvent(e)
        }
    }

    /**
     * Dispatches an event to all registered handlers within the event chain.
     *
     * @param e the event to be dispatched to the handlers.
     */
    private suspend fun dispatchEvent(e: E) {
        (intentsHandlers + eventsSender).forEach { handler ->
            handler.process(e)
        }
    }

    /**
     * Launches a coroutine to initiate the post-metadata handling process for event handlers.
     *
     * This method iterates over all available event and intent handlers, invoking the metadata
     * collection and processing mechanism for each handler. It operates asynchronously, ensuring
     * concurrent execution where applicable.
     */
    private fun startPostMetadataHandling() {
        coroutineScope.launch {
            (intentsHandlers + eventsSender).forEach { handler ->
                handlePostMetadata(handler)
            }
        }
    }

    private fun CoroutineScope.handlePostMetadata(handler: EventHandler<*>) {
        launch {
            handler.postMetadata.collect { meta ->
                postMiddleware(meta)
            }
        }
    }

    /**
     * Initiates the process of raw event collection for all registered event senders.
     */
    private fun startRawEventsListening() {
        coroutineScope.launch {
            eventsSender.forEach { sender ->
                launchRawEventsCollection(sender)
            }
        }
    }

    /**
     * Launches a coroutine to collect raw events from the provided sender.
     * Processes collected events by forwarding them to the appropriate handlers
     * based on their type (either `Message.Event` or `Message.Intent`).
     *
     * @param sender The event handler providing raw events to be processed.
     */
    private fun CoroutineScope.launchRawEventsCollection(sender: AbstractEventHandler<*>) {
        launch {
            sender.rawEvents.collect { (parentId, message) ->
                when (message) {
                    is Message.Event -> {
                        forwardEventsToEventSenders(parentId, message)
                    }

                    is Message.Intent -> {
                        forwardEventsToParameterHolders(parentId, message)
                    }
                }
            }
        }
    }

    private fun CoroutineScope.forwardEventsToParameterHolders(
        parentId: Uuid,
        message: Message.Intent
    ) {
        intentsHandlers.forEach { holder ->
            launch(CallMetadata(parentId, Uuid.random())) {
                holder.process(message)
            }
        }
    }

    private fun CoroutineScope.forwardEventsToEventSenders(
        parentId: Uuid,
        message: Message.Event
    ) {
        eventsSender.forEach { target ->
            launch(CallMetadata(parentId, Uuid.random())) {
                target.process(message)
            }
        }
    }
}