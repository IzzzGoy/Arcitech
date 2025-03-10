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
            private val dependencies = mutableMapOf<String, List<String>>()

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

            override fun <E : Message.Event, S : Any, I : Message.Intent> provide(
                eKClass: KClass<E>,
                sKClass: KClass<S>,
                iKClass: KClass<I>,
                key: String
            ): Container<E, S, I> {
                check(metadata[key] != null && metadata[key]?.iKClass?.equals(iKClass) == true) {
                    "Incompatible intent types for key: $key"
                }
                check(metadata[key] != null && metadata[key]?.eKClass?.equals(eKClass) == true) {
                    "Incompatible events types for key: $key"
                }
                check(metadata[key] != null && metadata[key]?.sKClass?.equals(sKClass) == true) {
                    "Incompatible state types for key: $key"
                }
                return active.getOrPut(key) {
                    factories[key]?.let { factory ->
                        val builder = ContainerBuilder<E, S, I>(scope.coroutineContext, this).also {
                            it.factory(this)
                        }
                        check(!hasCyclicDependency(key) && builder.metadata.none { it == key }) {
                            "Container with $key has cyclic dependencies"
                        }

                        dependencies[key] = builder.metadata

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


            private fun hasCyclicDependency(
                containerTag: String,
                visited: MutableSet<String> = mutableSetOf(),
                recursionStack: MutableSet<String> = mutableSetOf()
            ): Boolean {
                if (containerTag in recursionStack) return true
                if (containerTag in visited) return false

                visited.add(containerTag)
                recursionStack.add(containerTag)

                val dependencies = dependencies[containerTag] ?: emptyList()
                for (dep in dependencies) {
                    if (hasCyclicDependency(dep, visited, recursionStack)) {
                        return true
                    }
                }

                recursionStack.remove(containerTag)
                return false
            }

        }
    }
}

fun manager(block: ContainerManagerBuilder.() -> Unit): ContainerManager {
    return ContainerManagerBuilder().apply(block).build()
}
