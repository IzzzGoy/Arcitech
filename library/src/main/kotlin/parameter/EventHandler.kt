package parameter

import container.Message
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
): AutoCloseable {
    private val coroutineScope = CoroutineScope(coroutineContext)

    init {
        coroutineScope.launch {
            eventsSender.forEach { sender ->
                launch {
                    sender.events.collect { e ->
                        (intentsHandlers + eventsSender).forEach {
                            it.process(e)
                        }
                    }
                }
            }
        }
    }

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