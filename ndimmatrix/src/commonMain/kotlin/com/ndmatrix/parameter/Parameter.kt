package com.ndmatrix.parameter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface Parameter<S: Any> {
    val value: S
        get() = flow.value
    val flow: StateFlow<S>
}

@OptIn(ExperimentalUuidApi::class)
data class PostExecMetadata<E: Message>(
    val event: E,
    val duration: Duration,
    val parentId: Uuid?,
    val currentId: Uuid,
)

interface EventHandler<E: Message> {
    suspend fun handle(e: E)
    suspend fun process(e: Message)

    val postMetadata: Flow<PostExecMetadata<E>>
        get() = emptyFlow()
}

interface IntentHandler<E: Message.Intent> : EventHandler<E>

abstract class ParameterHolder<E: Message.Intent, S: Any>(initialValue: S) : Parameter<S>,
    IntentHandler<E> {
    private val _flow = MutableStateFlow(initialValue)


    override val flow: StateFlow<S> = _flow
    override val value: S get() = flow.value
    protected fun update(value: S) {
        _flow.value = value
    }
}