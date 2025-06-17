package com.ndmatrix.core.event

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    @OptIn(ExperimentalUuidApi::class)
    val rawEvents: Flow<Pair<Uuid, Message>>
}
/**
 * Contract to process message.
 * */
interface Processable {
    /**
     * Process given [Message].
     * */
    suspend fun process(e: Message)
}