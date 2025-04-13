package com.ndmatrix.parameter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

abstract class AbstractEventHandler<E: Message.Event>(
    coroutineContext: CoroutineContext
) : EventHandler<E> {
    private val _events: MutableSharedFlow<Message> = MutableSharedFlow()
    val events = _events.asSharedFlow()

    private val coroutineScope = CoroutineScope(coroutineContext)

    protected suspend fun returnEvent(block: () -> Message) {
        coroutineScope.launch {
            _events.emit(block())
        }
    }

}

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
                    sender.events.collect { e ->
                        if (e is Message.Event) {
                            (eventsSender).forEach {
                                it.process(e)
                            }
                        } else if (e is Message.Intent) {
                            intentsHandlers.forEach {
                                it.process(e)
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
         coroutineScope.launch {
             (intentsHandlers + eventsSender).forEach {
                 it.process(e)
             }
         }
    }
}