package com.ndmatrix.core.event

import com.ndmatrix.core.metadata.PostExecMetadata
import kotlinx.coroutines.flow.SharedFlow

/**
 * Base interface for all entities capable of handling [Message] instances.
 *
 * Provides a flow of post-execution metadata for observability and debugging.
 *
 * @param E the type of [Message] this handler processes.
 */
interface EventHandler<E : Message> {
    /**
     * Public shared flow emitting metadata after each message handling.
     */
    val postMetadata: SharedFlow<PostExecMetadata<*>>

    /**
     * Handles a message of type [E] with optimized logic.
     *
     * @param e the message instance to handle.
     */
    suspend fun handle(e: E)
}