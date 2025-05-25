package com.ndmatrix.core.event

import com.ndmatrix.core.metadata.CallMetadata
import com.ndmatrix.core.metadata.PostExecMetadata
import com.ndmatrix.core.parameter.ParameterHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Manages sequential or parallel execution of event handlers and parameter holders
 * as a tree or chain layout.
 *
 * @param E the type of the starting [Message.Event] that triggers the chain.
 * @param intentsHandlers a list of [com.ndmatrix.core.parameter.ParameterHolder] instances handling [Message.Intent]s.
 * @param eventsSender a list of [AbstractEventHandler] instances generating child events.
 * @param coroutineContext the coroutine context for the entire chain execution.
 * @param isDebug flag to enable collection of [com.ndmatrix.core.metadata.PostExecMetadata] for debugging purposes.
 *
 * @property coroutineScope the internal [kotlinx.coroutines.CoroutineScope] for launching chain coroutines.
 */
@OptIn(ExperimentalUuidApi::class)
@Suppress("UNUSED")
abstract class EventChain<E : Message.Event>(
    private val intentsHandlers: List<ParameterHolder<*, *>>,
    private val eventsSender: List<Chainable>,
    coroutineContext: CoroutineContext,
    isDebug: Boolean
) : AutoCloseable, Chainable {

    constructor(
        starter: AbstractEventHandler<E>,
        coroutineContext: CoroutineContext,
        isDebug: Boolean = true,
        parameterHolders: List<ParameterHolder<*, *>> = emptyList(),
        chainables: List<Chainable> = emptyList(),
    ) : this(
        intentsHandlers = parameterHolders,
        eventsSender = chainables + starter,
        coroutineContext = coroutineContext,
        isDebug = isDebug
    )

    private val coroutineScope = CoroutineScope(coroutineContext)

    /**
     * Emits a merged flow of all events collected from the registered event senders.
     *
     * This flow combines the `events` from each sender into a single unified stream,
     * allowing for centralized handling of `Message.Event` instances.
     *
     * The resulting flow includes all event types as defined by the `Message.Event` interface,
     * acting as an entry point for external consumers to interact with the event chain.
     */
    override val events: SharedFlow<Message> =
        merge(*eventsSender.map { it.events }.toTypedArray())
            .shareIn(coroutineScope, SharingStarted.Eagerly)

    override val rawEvents: Flow<Pair<Uuid, Message>> =
        merge(*eventsSender.map { it.rawEvents }.toTypedArray())
            .shareIn(coroutineScope, SharingStarted.Eagerly)

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
        dispatchEventWithMetadata(null, e)
    }

    /**
     * Handles a message.
     * Propagate parent event ID to chain parent and child event chains.
     *
     * @param e the message instance to handle.
     */
    override suspend fun process(e: Message) {
        val parentId = coroutineContext[CallMetadata.CallMetadataKey]?.parentId
        dispatchEventWithMetadata(parentId, e)
    }

    /**
     * Extract parent event ID metadata and dispatches an event to all registered handlers within
     * the event chain.
     *
     * @param e the event to be dispatched to the handlers.
     * @param parentId the parent event ID if exists.
     */
    private fun dispatchEventWithMetadata(parentId: Uuid?, e: Message) {
        coroutineScope.launch(CallMetadata(parentId, Uuid.random())) {
            dispatchEvent(e)
        }
    }

    /**
     * Dispatches an event to all registered handlers within the event chain.
     *
     * @param e the event to be dispatched to the handlers.
     */
    private suspend fun dispatchEvent(e: Message) {
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
                if (handler is PostMetadataEventHandler<*>) {
                    handlePostMetadata(handler)
                }
            }
        }
    }

    private fun CoroutineScope.handlePostMetadata(handler: PostMetadataEventHandler<*>) {
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
    private fun CoroutineScope.launchRawEventsCollection(sender: Chainable) {
        launch {
            sender.rawEvents.collect { (parentId, message) ->
                forwardEventsToConsumers(message, parentId)
            }
        }
    }

    private fun CoroutineScope.forwardEventsToConsumers(
        message: Message,
        parentId: Uuid?
    ) {
        when (message) {
            is Message.Event -> {
                forwardEventsToEventSenders(parentId, message)
            }

            is Message.Intent -> {
                forwardEventsToParameterHolders(parentId, message)
            }
        }
    }

    private fun CoroutineScope.forwardEventsToParameterHolders(
        parentId: Uuid?,
        message: Message.Intent
    ) {
        intentsHandlers.forEach { holder ->
            if (holder.canProcessed(message)) {
                launch(CallMetadata(parentId, Uuid.Companion.random())) {
                    holder.process(message)
                }
            }
        }
    }

    private fun CoroutineScope.forwardEventsToEventSenders(
        parentId: Uuid?,
        message: Message.Event
    ) {
        eventsSender.forEach { target ->
            if ((target is AbstractEventHandler<*> && target.canProcessed(message)) || target is EventChain<*>) {
                launch(CallMetadata(parentId, Uuid.Companion.random())) {
                    target.process(message)
                }
            }
        }
    }
}