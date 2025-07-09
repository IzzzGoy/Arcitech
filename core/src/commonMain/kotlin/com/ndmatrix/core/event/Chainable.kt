package com.ndmatrix.core.event

import com.ndmatrix.core.metadata.ExecutionMetadata
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Contract, that allows to chain event processing into chains.
 * Organize events upstreeam by [events]/[rawEvents] and downstream by [process] from [Processable].
 * */
interface Chainable: Processable {

    /**
     * Flow of upstream events. Same flow as [rawEvents] but without execution metadata.
     * */
    val events: Flow<Message>
    /**
     * Flow of upstream events with metadata.
     * */
    val rawEvents: Flow<ExecutionMetadata>
}
/**
 * Contract to process message.
 * @property metadata a set of [KClass] that can be processed by this handler.
 * */
interface Processable {
    /**
     * Process given [Message].
     * */
    suspend fun process(e: Message)

    val metadata: Set<KClass<out Message>>
        get() = emptySet()
}