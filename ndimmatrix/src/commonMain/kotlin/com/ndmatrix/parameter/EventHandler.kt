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

class CallMetadata @OptIn(ExperimentalUuidApi::class) constructor(
    val parentId: Uuid? = null,
    val currentId: Uuid = Uuid.random(),
): CoroutineContext.Element {
    companion object {
        val CallMetadataKey = object : CoroutineContext.Key<CallMetadata>{}
    }
    override val key: CoroutineContext.Key<*>
        get() = CallMetadataKey
}


@OptIn(ExperimentalUuidApi::class)
abstract class AbstractEventHandler<E: Message.Event>(
    coroutineContext: CoroutineContext
) : EventHandler<E> {
    private val coroutineScope = CoroutineScope(coroutineContext)
    private val _events: MutableSharedFlow<Pair<Uuid, Message>> = MutableSharedFlow()
    val events = _events.map { it.second }.shareIn(coroutineScope, SharingStarted.Eagerly)
    val rowEvents = _events.asSharedFlow()



    protected suspend fun returnEvent(block: () -> Message) {
        val id = coroutineContext[CallMetadataKey]!!.currentId
        coroutineScope.launch {
            _events.emit(id to block())
        }
    }

}

@OptIn(ExperimentalUuidApi::class)
abstract class EventChain<E: Message.Event>(
    private val intentsHandlers: List<ParameterHolder<*, *>>,
    private val eventsSender: List<AbstractEventHandler<*>>,
    coroutineContext: CoroutineContext,
    isDebug: Boolean,
): AutoCloseable {
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
                    sender.rowEvents.collect { (id, e) ->
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

    protected open fun postMiddleware(postExecMetadata: PostExecMetadata<*>) {}

    override fun close() {
        coroutineScope.cancel()
    }

    fun general(e: E) {
         coroutineScope.launch(CallMetadata(null, Uuid.random())) {
             (intentsHandlers + eventsSender).forEach {
                 it.process(e)
             }
         }
    }
}