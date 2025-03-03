package manager

import container.Container
import container.ContainerBuilder
import container.ContainerProvider
import container.Message
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass

interface ContainerManager: ContainerProvider {
    val scope: CoroutineScope
    fun send(message: Message)
}

internal class Metadata<E: Message.Event, S: Any, I: Message.Intent> (
    val eKClass: KClass<E>,
    val sKClass: KClass<S>,
    val iKClass: KClass<I>
)

class ContainerManagerBuilder() {
    private var context: CoroutineContext = EmptyCoroutineContext

    fun context(context: CoroutineContext) {
        this.context = context
    }

    private var transform: (CoroutineContext, Throwable) -> Message = { _: CoroutineContext, _: Throwable -> error("Unhandled exception. Specify root exceptions transform!")  }

    fun transform(block: (CoroutineContext, Throwable) -> Message) {
        this.transform = block
    }

    private val factories: MutableMap<String, ContainerBuilder<*, *, *>.(ContainerProvider) -> Unit> = mutableMapOf()
    private val metadata: MutableMap<String, Metadata<*, *, *>> = mutableMapOf()


    inline fun <reified E: Message.Event, reified S: Any, reified I: Message.Intent> container(key: String, noinline block: ContainerBuilder<E, S, I>.(ContainerProvider) -> Unit) {
        container(key, E::class, S::class, I::class, block)
    }

    fun <E: Message.Event, S: Any, I: Message.Intent> container(
        key: String,
        eKClass: KClass<E>,
        sKClass: KClass<S>,
        iKClass: KClass<I>,
        block: ContainerBuilder<E, S, I>.(ContainerProvider) -> Unit
    ) {
        metadata[key] = Metadata(eKClass, sKClass, iKClass)
        factories[key] = block as ContainerBuilder<*, *, *>.(ContainerProvider) -> Unit
    }


    fun build(): ContainerManager {
        return object : ContainerManager {

            private val active = mutableMapOf<String, Container<*, *, *>>()
            private val metadata = this@ContainerManagerBuilder.metadata
            private val factories = this@ContainerManagerBuilder.factories
            private val eventConsumers = mutableMapOf<String, List<KClass<out Message.Event>>>()
            private val observers = mutableMapOf<String, Job>()

            override val scope: CoroutineScope by lazy {
                CoroutineScope(SupervisorJob() + context + CoroutineExceptionHandler { coroutineContext, throwable ->
                    send(this@ContainerManagerBuilder.transform(coroutineContext, throwable))
                })
            }

            override fun send(message: Message) {
                // TODO
                when (message) {
                    is Message.Event -> {
                        eventConsumers.forEach { (k, v) ->
                            if (v.any { it.isInstance(message) }) {
                                scope.launch {
                                    active[k]?.sendMessage(message)
                                }
                            }
                        }
                    }

                    is Message.Intent -> {
                        for ((k, v) in metadata) {
                            if (v.iKClass.isInstance(message)) {
                                scope.launch {
                                    active[k]?.sendMessage(message)
                                }
                                break
                            }
                        }
                    }
                }
            }

            override fun <E : Message.Event, S : Any, I : Message.Intent> provide(key: String): Container<E, S, I> {
                return active.getOrPut(key) {
                    factories[key]?.let { factory ->
                        val builder = ContainerBuilder<E, S, I>(scope.coroutineContext).also {
                            it.factory(this)

                        }

                        val container = builder.build().also {
                            observers[key] = scope.launch {
                                it.events.collect { event ->
                                    send(event)
                                }
                            }
                            eventConsumers[key] = builder.eventsHandlersMetadata
                        }

                        container
                    } ?: throw IllegalArgumentException("No factory found for key: $key")
                } as Container<E, S, I>
            }
        }
    }
}

fun manager(block: ContainerManagerBuilder.() -> Unit): ContainerManager {
    return ContainerManagerBuilder().apply(block).build()
}
