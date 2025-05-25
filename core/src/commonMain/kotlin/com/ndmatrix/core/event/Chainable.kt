package com.ndmatrix.core.event

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface Chainable: Processable {

    val events: Flow<Message>
    @OptIn(ExperimentalUuidApi::class)
    val rawEvents: Flow<Pair<Uuid, Message>>
}

interface Processable {
    suspend fun process(e: Message)
}