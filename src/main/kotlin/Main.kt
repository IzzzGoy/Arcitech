import container.ContainerBuilder
import container.Message
import container.eventHandler
import container.source


interface A : Message.Event {
    object A1: A
}
class B : Message.Intent


fun main(args: Array<String>) {
    val container: ContainerBuilder<A, Int, B>.() -> Unit = {

        source(1) {
            reduce { i, _ ->
                i + 1
            }
        }

        eventHandler(A::class) {
            it.toString()
        }

        eventHandler<A.A1> {
            it.toString()
        }
    }
}