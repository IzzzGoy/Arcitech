package container

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

sealed interface Message {
    interface Event : Message
    interface Intent : Message
}

sealed interface StateProxy<S: Any, I: Message.Intent> : AutoCloseable {
    interface Source<S: Any, I: Message.Intent> : StateProxy<S, I> {
        class Factory<S: Any, I: Message.Intent>(initial: S) {

            private var context: CoroutineContext = EmptyCoroutineContext
            private val innerFlow = MutableStateFlow(initial)
            private var reducer: suspend (S, I) -> S = { _, _ -> initial }

            fun reduce(block: suspend (S, I) -> S) {
                reducer = block
            }

            fun context(context: CoroutineContext) {
                this.context = context
            }

            fun build(): StateProxy<S, I> {
                return object : Source<S, I> {

                    private val scope = CoroutineScope(context)

                    override val flow: Flow<S> = innerFlow

                    override fun reduce(intent: I) {
                        scope.launch {
                            innerFlow.value = reducer(innerFlow.value, intent)
                        }
                    }

                    override fun close() {
                        scope.cancel()
                    }
                }
            }
        }
    }

    interface Synthetic<S: Any, I: Message.Intent> : StateProxy<S, I> {
        class Factory<S: Any, I: Message.Intent> {

            private var context: CoroutineContext = EmptyCoroutineContext
            private var provider: () -> Flow<S> = throw IllegalStateException("No provider")

            fun<T1: Any, T2: Any> syntez(cT1: Container<*, T1, *>, cT2: Container<*, T2, *>, rule: (Flow<T1>, Flow<T2>) -> Flow<S>) {
                provider = { rule(cT1.state.flow, cT2.state.flow) }
            }

            fun <T1: Any, T2: Any, T3: Any> syntez(cT1: Container<*, T1, *>, cT2: Container<*, T2, *>, cT3: Container<*, T3, *>, rule: (Flow<T1>, Flow<T2>, Flow<T3>) -> Flow<S>) {
                provider = { rule(cT1.state.flow, cT2.state.flow, cT3.state.flow) }
            }

            fun <T1: Any, T2: Any, T3: Any, T4: Any> syntez(cT1: Container<*, T1, *>, cT2: Container<*, T2, *>, cT3: Container<*, T3, *>, cT4: Container<*, T4, *>, rule: (Flow<T1>, Flow<T2>, Flow<T3>, Flow<T4>) -> Flow<S>) {
                provider = { rule(cT1.state.flow, cT2.state.flow, cT3.state.flow, cT4.state.flow) }
            }

            fun <T1: Any, T2: Any, T3: Any, T4: Any, T5: Any> syntez(cT1: Container<*, T1, *>, cT2: Container<*, T2, *>, cT3: Container<*, T3, *>, cT4: Container<*, T4, *>, cT5: Container<*, T5, *>, rule: (Flow<T1>, Flow<T2>, Flow<T3>, Flow<T4>, Flow<T5>) -> Flow<S>) {
                provider = { rule(cT1.state.flow, cT2.state.flow, cT3.state.flow, cT4.state.flow, cT5.state.flow) }
            }


            fun context(context: CoroutineContext) {
                this.context = context
            }

            fun build(): StateProxy<S, I> {
                return object : Synthetic<S, I> {

                    private val scope = CoroutineScope(context)

                    override val flow: Flow<S> = provider().shareIn(scope = scope, started = SharingStarted.Lazily)

                    override fun reduce(intent: I) = throw UnsupportedOperationException(
                        "Synthetic state doesn't support direct reduction"
                    )

                    override fun close() {
                        scope.cancel("Synthetic scope cancelled")
                    }
                }
            }
        }
    }

    class None<S: Any, I: Message.Intent> : StateProxy<S, I> {
        override val flow: Flow<S> = throw IllegalStateException("No source")
        override fun close() {}

        override fun reduce(intent: I) = throw IllegalStateException("No source")
    }

    val flow: Flow<S>
    fun reduce(intent: I)
}

fun<S: Any, I: Message.Intent> ContainerBuilder<*, S, I>.source(initial: S, block: StateProxy.Source.Factory<S, I>.() -> Unit) {
    stateProxy = StateProxy.Source.Factory<S, I>(initial).apply(block).build()
}

interface Container<E: Message.Event, S: Any, I: Message.Intent>: AutoCloseable {
    val events: Flow<E>
    val state: StateProxy<S, I>

    fun sendMessage(message: Message)
}

class ContainerBuilder<E: Message.Event, S: Any, I: Message.Intent> {

    internal class EventHandler<E: Message.Event>(
        val eventClass: KClass<E>,
        val handler: suspend (E) -> Unit
    ) {
        suspend operator fun invoke(event: Message.Event) {
            if (eventClass.isInstance(event)) {
                handler(event as? E ?: throw IllegalArgumentException("Expected $eventClass, but received ${event::class.java.simpleName}"))
            }
        }
    }

    private val eventsHandlers: MutableList<EventHandler<out Message.Event>> = mutableListOf()
    val eventsHandlersMetadata
        get() = eventsHandlers.map { it.eventClass }

    fun<T: Message.Event> eventHandler(eventClass: KClass<T>, handler: suspend (T) -> Unit) {
        eventsHandlers.add(EventHandler(eventClass, handler))
    }

    var stateProxy: StateProxy<S, I> = StateProxy.None()
}

inline fun<reified T: Message.Event> ContainerBuilder<*, *, *>.eventHandler(noinline handler: suspend (T) -> Unit) {
    eventHandler(T::class, handler)
}


