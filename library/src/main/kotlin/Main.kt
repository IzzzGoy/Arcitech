import container.Message
import container.eventHandler
import kotlinx.coroutines.*
import manager.manager

sealed interface E: Message.Event {
    data object E1: E
    data object E2: E
}

sealed interface A: Message.Intent {
    data object Inc: A
    data object Dec: A
}

data class Error(val message: String) : Message.Event

fun main(args: Array<String>) {
    runBlocking {
        val manager = manager {
            context(Dispatchers.Default)

            transform { coroutineContext, throwable ->
                Error(throwable.message.orEmpty())
            }

            container<E, Int, A>("Test") {
                coroutineContext(Dispatchers.Default)

                source(1) {
                    reduce { acc, intent ->
                        when (intent) {
                            is A.Inc -> acc + 1
                            is A.Dec -> acc - 1
                        }
                    }
                }

                eventHandler<E.E1> {
                    println("Event E1 received")  // Output: Event E1 received
                }

                eventHandler<E.E2> {
                    event {
                        E.E1
                    }

                    intent {
                        A.Inc
                    }
                }
            }
        }

        /*val container = manager.provide<E, Int, A>("Test")
        launch {
            container.state.flow.collect { println(it) } // Output: 1, 2
        }
        delay(100)
        manager.send(E.E1)
        manager.send(A.Inc)
        manager.send(E.E2)*/
    }
}