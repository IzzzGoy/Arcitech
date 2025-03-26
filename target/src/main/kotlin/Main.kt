import com.exmaple.target.*
import container.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import parameter.PostExecMetadata
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class CounterImpl : CounterParameterHolder() {

    override fun handleIncrement(intent: CounterIntents.Increment, state: Int): Int {
        return state + 1
    }

    override fun handleDecrement(intent: CounterIntents.Decrement, state: Int): Int {
        return state - 1
    }

    override fun handleAdd(intent: CounterIntents.Add, state: Int): Int {
        return state + intent.number
    }

}

class StringHolder : StringHolderParameterHolder() {
    override fun handleSet(intent: StringHolderIntents.Set, state: String): String {
        return intent.text
    }

}

class ProjectionGenericModelImpl(
    counterImpl: CounterParameterHolder,
    stringHolderParameterHolder: StringHolderParameterHolder
) : ProjectionGenericModel(counterImpl, stringHolderParameterHolder, Dispatchers.Default) {
    override fun project(Counter: Int, StringHolder: String): ProjectionModelGenericModel {
        return ProjectionModelGenericModel(Counter, StringHolder)
    }
}

class ProjectionCombineImpl(
    counter: CounterParameterHolder,
    genericModel: ProjectionGenericModel,
    stringHolder: StringHolder
) : ProjectionCombine(
    counter, genericModel, stringHolder, Dispatchers.Default
) {
    override fun project(
        Counter: Int,
        GenericModel: ProjectionModelGenericModel,
        StringHolder: String
    ): ProjectionModelCombine {
        return ProjectionModelCombine(Counter, GenericModel, StringHolder)
    }

}

class AnotherEventHandler(coroutineContext: CoroutineContext) : EventAnotherEventHandler(coroutineContext) {

    override suspend fun handle(e: EventAnotherEvent) {
        println("ANOTHER EVENT")
        returnEvent {
            EventTestEvent(15)
        }
    }
}

class EventTestEventHandlerImpl(coroutineContext: CoroutineContext) : EventTestEventHandler(coroutineContext) {
    override suspend fun handle(e: EventTestEvent) {
        println(e)
        returnEvent {
            CounterIntents.Increment
        }
    }

}


suspend fun main() {
    println("!")

    val context = currentCoroutineContext()
    val counter = CounterImpl()
    println("!")
    val stringHolder = StringHolder()
    val genericModel = ProjectionGenericModelImpl(counter, stringHolder)
    val projectionCombine = ProjectionCombineImpl(counter, genericModel, stringHolder)
    val eventTestEventHandler = EventTestEventHandlerImpl(EmptyCoroutineContext)
    println("!")

    //val chain = TestEventChain(Dispatchers.Default, eventTestEventHandler, counter)
    val anotherChain = object : AnotherEventChain(
        context,
        AnotherEventHandler(context),
        eventTestEventHandler,
        counter
    ) {
        override fun postMiddleware(postExecMetadata: PostExecMetadata<*>) {
            super.postMiddleware(postExecMetadata)
            println(postExecMetadata)
        }
    }

    //chain.general(EventTestEvent(43))
    delay(300)

    anotherChain.general(EventAnotherEvent)


    counter.handle(CounterIntents.Increment) // Output: 1

    counter.handle(CounterIntents.Decrement) // Output: 0

    counter.handle(CounterIntents.Add(3)) // Output: 5





    stringHolder.handle(StringHolderIntents.Set("Hello, World!")) // Output: Hello, World!
    stringHolder.process(CounterIntents.Add(3)) // Output: Hello, World!
    delay(300)
    println(stringHolder.value)
    println(genericModel.value)
    println(projectionCombine.value)
}