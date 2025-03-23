import com.exmaple.target.*
import com.exmaple.target.Set
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlin.coroutines.CoroutineContext

class CounterImpl : CounterParameterHolder() {

    override fun handleIncrement(intent: Increment, state: Int): Int {
        return state + 1
    }

    override fun handleDecrement(intent: Decrement, state: Int): Int {
        return state - 1
    }

    override fun handleAdd(intent: Add, state: Int): Int {
        return state + intent.number
    }

}

class StringHolder : StringHolderParameterHolder() {
    override fun handleSet(intent: Set, state: String): String {
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


suspend fun main() {

    val counter = CounterImpl()
    val stringHolder = StringHolder()
    val genericModel = ProjectionGenericModelImpl(counter, stringHolder)

    counter.handle(Increment) // Output: 1
    println(counter.value)
    println(genericModel.value)
    counter.handle(Decrement) // Output: 0
    println(counter.value)
    println(genericModel.value)
    counter.handle(Add(3)) // Output: 5
    println(counter.value)
    println(genericModel.value)




    stringHolder.handle(Set("Hello, World!")) // Output: Hello, World!
    delay(300)
    println(stringHolder.value)
    println(genericModel.value)
}