package parameter

import container.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface Parameter<S: Any> {
    val value: S
        get() = flow.value
    val flow: StateFlow<S>
}

interface EventHandler<E: Message> {
    suspend fun handle(e: E)
    suspend fun process(e: Message)
}

interface IntentHandler<E: Message.Intent> : EventHandler<E>

abstract class ParameterHolder<E: Message.Intent, S: Any>(initialValue: S) : Parameter<S>, IntentHandler<E> {
    private val _flow = MutableStateFlow(initialValue)


    override val flow: StateFlow<S> = _flow
    override val value: S get() = flow.value
    protected fun update(value: S) {
        _flow.value = value
    }
}