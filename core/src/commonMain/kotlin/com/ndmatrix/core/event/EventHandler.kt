package com.ndmatrix.core.event

/**
 * Base interface for all entities capable of handling [Message] instances.
 *
 * Provides a flow of post-execution metadata for observability and debugging.
 *
 * @param E the type of [Message] this handler processes.
 */
interface EventHandler<E : Message> {
    /**
     * Handles a message of type [E] with optimized logic.
     *
     * @param e the message instance to handle.
     */
    suspend fun handle(e: E)
}