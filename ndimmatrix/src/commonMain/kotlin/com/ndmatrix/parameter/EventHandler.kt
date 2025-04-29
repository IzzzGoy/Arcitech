package com.ndmatrix.parameter

import com.ndmatrix.parameter.CallMetadata.Companion.CallMetadataKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Represents metadata of current event execution.
 *
 * @property parentId id of parent event. null only on start event chain.
 * @property currentId id of current event in scope of current execution.
 * */
class CallMetadata @OptIn(ExperimentalUuidApi::class) constructor(
    val parentId: Uuid? = null,
    val currentId: Uuid = Uuid.random(),
) : CoroutineContext.Element {
    companion object {
        val CallMetadataKey = object : CoroutineContext.Key<CallMetadata> {}
    }

    override val key: CoroutineContext.Key<*>
        get() = CallMetadataKey
}

/**
 * Base event handle infrastructure. Contains routine code of event handler.
 *
 * @param coroutineContext context to observe execution results.
 *
 * @property coroutineScope scope to observe execution results.
 * @property _events inner flow of output events data stream.
 * @property events flow of output events data stream. Contains only events, without any other metadata.
 * @property rawEvents flow of output events data stream. Contains events with parent id.
 * */
@OptIn(ExperimentalUuidApi::class)
abstract class AbstractEventHandler<E : Message.Event>(
    coroutineContext: CoroutineContext
) : EventHandler<E>() {
    private val coroutineScope = CoroutineScope(coroutineContext)
    private val _events: MutableSharedFlow<Pair<Uuid, Message>> = MutableSharedFlow()
    val events = _events.map { it.second }.shareIn(coroutineScope, SharingStarted.Eagerly)
    val rawEvents = _events.asSharedFlow()

    /**
     * Emits event, created by given lambda, to output events stream.
     *
     * @param block lambda-factory of output event.
     * */
    protected suspend fun returnEvent(block: () -> Message) {
        val id = coroutineContext[CallMetadataKey]!!.currentId
        coroutineScope.launch {
            _events.emit(id to block())
        }
    }

}

/**
 * EventChain represent sequence/tree of event handlers.
 *
 * @param intentsHandlers list of [ParameterHolder]. They can`t produce any event.
 * @param eventsSender list of [EventHandler]. They can produce any event.
 * This separation uses for inner optimizations.
 * @param coroutineContext context of events execution.
 * @param isDebug enables some debug options, like observing post execution metadata.
 *
 * @property coroutineScope scope of events execution.
 * */
@OptIn(ExperimentalUuidApi::class)
abstract class EventChain<E : Message.Event>(
    private val intentsHandlers: List<ParameterHolder<*, *>>,
    private val eventsSender: List<AbstractEventHandler<*>>,
    coroutineContext: CoroutineContext,
    isDebug: Boolean,
) : AutoCloseable {
    private val coroutineScope = CoroutineScope(coroutineContext)

    init {
        if (isDebug) {
            coroutineScope.launch {
                (intentsHandlers + eventsSender).forEach {
                    launch {
                        it.postMetadata.collect {
                            postMiddleware(it)
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            eventsSender.forEach { sender ->
                launch {
                    sender.rawEvents.collect { (id, e) ->
                        if (e is Message.Event) {
                            (eventsSender).forEach {
                                launch(CallMetadata(id, Uuid.random())) {
                                    it.process(e)
                                }

                            }
                        } else if (e is Message.Intent) {
                            intentsHandlers.forEach {
                                launch(CallMetadata(id, Uuid.random())) {
                                    it.process(e)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Process post execution metadata. May use to logs, analytics, debug, etc.
     *
     * @param postExecMetadata post execution metadata of some event.
     * */
    protected open fun postMiddleware(postExecMetadata: PostExecMetadata<*>) {}

    /**
     * Close and terminate chain execution. May be auto-invoke by GC by [AutoCloseable].
     * */
    override fun close() {
        coroutineScope.cancel()
    }

    /**
     * Start event chain by specified start event.
     *
     * @param start event. [E] must be only [Message.Event]. Intent can`t start chain at this project state.
     * */
    fun general(e: E) {
        coroutineScope.launch(CallMetadata(null, Uuid.random())) {
            (intentsHandlers + eventsSender).forEach {
                it.process(e)
            }
        }
    }
}