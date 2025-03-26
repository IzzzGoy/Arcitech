package container

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

sealed interface Message {
    interface Event : Message
    interface Intent : Message
}

sealed interface StateProxy<S : Any, I : Message.Intent> : AutoCloseable {
    interface Source<S : Any, I : Message.Intent> : StateProxy<S, I> {
        class Factory<S : Any, I : Message.Intent>(private val initial: S, private val parentContext: CoroutineContext) {

            private var context: CoroutineContext = parentContext
            private val innerFlow = MutableStateFlow(initial)
            private var reducer: suspend (S, I) -> S = { _, _ -> initial }
            private val middleWares: MutableList< suspend (S) -> Unit> = mutableListOf()

            fun reduce(block: suspend (S, I) -> S) {
                reducer = block
            }

            fun context(context: CoroutineContext) {
                this.context = parentContext + context
            }

            fun useMiddleware(block: suspend (S) -> Unit) {
                middleWares += block
            }

            fun build(): StateProxy<S, I> {
                return object : Source<S, I>, UpdatableSource<S> {

                    private val scope = CoroutineScope(context)

                    override val flow: Flow<S> = innerFlow.onEach { s ->
                        middleWares.forEach { it(s) }
                    }.stateIn(
                        scope = scope,
                        initialValue = initial,
                        started = SharingStarted.WhileSubscribed(5_000L)
                    )

                    override val state: StateFlow<S>
                        get() = innerFlow

                    override fun invoke(value: S) {
                        innerFlow.value = value
                    }

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

    interface Proxy<S : Any, I : Message.Intent> : StateProxy<S, I> {
        class Factory<S : Any, I : Message.Intent>(
            private val parentContext: CoroutineContext,
            private val source: UpdatableSource<S>
        ) {
            private var context: CoroutineContext = parentContext
            private var reducer: suspend (S, I) -> S = { _, _ -> source.state.value }
            private val middleWares: MutableList< suspend (S) -> Unit> = mutableListOf()


            fun context(context: CoroutineContext) {
                this.context = parentContext + context
            }

            fun useMiddleware(block: suspend (S) -> Unit) {
                middleWares += block
            }


            fun build(): StateProxy<S, I> {
                return object : Proxy<S, I>, UpdatableSource<S> by source {
                    private val scope = CoroutineScope(context)

                    override val flow: Flow<S> = source.state.onEach { s ->
                        middleWares.forEach { it(s) }
                    }.stateIn(
                        scope = scope,
                        initialValue = source.state.value,
                        started = SharingStarted.WhileSubscribed(5_000L)
                    )

                    override val state: StateFlow<S>
                        get() = source.state

                    override fun reduce(intent: I) {
                        scope.launch {
                            source(reducer(state.value, intent))
                        }
                    }

                    override fun close() {
                        scope.cancel()
                    }
                }
            }
        }
    }

    interface Synthetic<S : Any, I : Message.Intent> : StateProxy<S, I> {
        class Factory<S : Any, I : Message.Intent>(private val parentContext: CoroutineContext) {

            private var context: CoroutineContext = parentContext
            private var provider: () -> Flow<S> = ::emptyFlow
            private val middleWares: MutableList< suspend (S) -> Unit> = mutableListOf()


            fun useMiddleware(block: suspend (S) -> Unit) {
                middleWares += block
            }

            fun syntez(rule: () -> Flow<S>) {
                provider = rule
            }

            fun <T1 : Any> syntez(cT1: Container<*, T1, *>, rule: (Flow<T1>) -> Flow<S>) {
                provider = { rule(cT1.state.flow) }
            }

            fun <T1 : Any, T2 : Any> syntez(
                cT1: Container<*, T1, *>,
                cT2: Container<*, T2, *>,
                rule: (Flow<T1>, Flow<T2>) -> Flow<S>
            ) {
                provider = { rule(cT1.state.flow, cT2.state.flow) }
            }

            fun <T1 : Any, T2 : Any, T3 : Any> syntez(
                cT1: Container<*, T1, *>,
                cT2: Container<*, T2, *>,
                cT3: Container<*, T3, *>,
                rule: (Flow<T1>, Flow<T2>, Flow<T3>) -> Flow<S>
            ) {
                provider = { rule(cT1.state.flow, cT2.state.flow, cT3.state.flow) }
            }

            fun <T1 : Any, T2 : Any, T3 : Any, T4 : Any> syntez(
                cT1: Container<*, T1, *>,
                cT2: Container<*, T2, *>,
                cT3: Container<*, T3, *>,
                cT4: Container<*, T4, *>,
                rule: (Flow<T1>, Flow<T2>, Flow<T3>, Flow<T4>) -> Flow<S>
            ) {
                provider = { rule(cT1.state.flow, cT2.state.flow, cT3.state.flow, cT4.state.flow) }
            }

            fun <T1 : Any, T2 : Any, T3 : Any, T4 : Any, T5 : Any> syntez(
                cT1: Container<*, T1, *>,
                cT2: Container<*, T2, *>,
                cT3: Container<*, T3, *>,
                cT4: Container<*, T4, *>,
                cT5: Container<*, T5, *>,
                rule: (Flow<T1>, Flow<T2>, Flow<T3>, Flow<T4>, Flow<T5>) -> Flow<S>
            ) {
                provider = { rule(cT1.state.flow, cT2.state.flow, cT3.state.flow, cT4.state.flow, cT5.state.flow) }
            }


            fun context(context: CoroutineContext) {
                this.context = parentContext + context
            }

            fun build(): StateProxy<S, I> {
                return object : Synthetic<S, I> {

                    private val scope = CoroutineScope(context)

                    override val flow: Flow<S> = provider().onEach { s ->
                        middleWares.forEach { it(s) }
                    }.shareIn(
                        scope = scope,
                        started = SharingStarted.WhileSubscribed(5_000L)
                    )

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

    class None<S : Any, I : Message.Intent> : StateProxy<S, I> {
        override val flow: Flow<S> = emptyFlow()
        override fun close() {}

        override fun reduce(intent: I) = throw IllegalStateException("No source")
    }

    val flow: Flow<S>
    fun reduce(intent: I)
}

interface Container<E : Message.Event, S : Any, I : Message.Intent> : AutoCloseable {
    val events: Flow<E>
    val state: StateProxy<S, I>

    suspend fun sendMessage(message: Message)
}

class ContainerBuilder<E : Message.Event, S : Any, I : Message.Intent>(
    private val parentContext: CoroutineContext,
    val containerProvider: ContainerProvider
) {

    private var scope: CoroutineScope = CoroutineScope(parentContext)

    fun coroutineContext(context: CoroutineContext) {
        scope = CoroutineScope(parentContext + context)
    }

    internal class EventHandler<E : Message.Event>(
        val eventClass: KClass<E>,
        val handler: suspend (E) -> Unit
    ) {
        suspend operator fun invoke(event: Message.Event) {
            if (eventClass.isInstance(event)) {
                handler(
                    event as? E
                        ?: throw IllegalArgumentException("Expected $eventClass, but received ${event::class.simpleName}")
                )
            }
        }
    }

    private val eventsHandlers: MutableList<EventHandler<out Message.Event>> = mutableListOf()
    val eventsHandlersMetadata
        get() = eventsHandlers.map { it.eventClass }

    fun <T : Message.Event> eventHandler(eventClass: KClass<T>, handler: suspend (T) -> Unit) {
        eventsHandlers.add(EventHandler(eventClass, handler))
    }

    private var stateProxy: StateProxy<S, I> = StateProxy.None()

    fun source(initial: S, block: StateProxy.Source.Factory<S, I>.() -> Unit) {
        stateProxy = StateProxy.Source.Factory<S, I>(initial, parentContext).apply(block).build()
    }

    fun synthetic(block: StateProxy.Synthetic.Factory<S, I>.() -> Unit) {
        stateProxy = StateProxy.Synthetic.Factory<S, I>(parentContext).apply(block).build()
    }

    fun proxy(source: UpdatableSource<S>, block: StateProxy.Proxy.Factory<S, I>.() -> Unit) {
        stateProxy = StateProxy.Proxy.Factory<S, I>(parentContext, source).apply(block).build()
    }

    private var events = MutableSharedFlow<E>()

    fun event(block: suspend () -> E) {
        scope.launch {
            events.emit(block())
        }
    }

    fun intent(block: suspend () -> I) {
        scope.launch {
            stateProxy.reduce(block())
        }
    }

    val metadata = mutableListOf<String>()


    inline fun <reified T1 : Message.Event, reified T2 : Any, reified T3 : Message.Intent> provide(tag: String): Container<T1, T2, T3> {
        metadata.add(tag)

        return containerProvider.provide(tag)
    }

    fun build(): Container<E, S, I> {
        return object : Container<E, S, I> {
            override val events: Flow<E> = this@ContainerBuilder.events
            override val state: StateProxy<S, I> = stateProxy

            override suspend fun sendMessage(message: Message) {
                when {
                    message is Message.Event -> {
                        scope.launch {
                            eventsHandlers.filter {
                                it.eventClass.isInstance(message)
                            }.forEach {
                                it(message)
                            }
                        }
                    }

                    message is Message.Intent && message as? I != null -> {
                        stateProxy.reduce(message)
                    }

                    else -> {
                        throw IllegalArgumentException("Expected Message.Event or Message.Intent, but received ${message::class.simpleName}")
                    }
                }

            }

            override fun close() {
                scope.cancel()
            }
        }
    }
}

inline fun <reified T : Message.Event> ContainerBuilder<*, *, *>.eventHandler(noinline handler: suspend (T) -> Unit) {
    eventHandler(T::class, handler)
}




