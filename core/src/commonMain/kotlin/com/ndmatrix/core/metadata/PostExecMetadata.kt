package com.ndmatrix.core.metadata

import com.ndmatrix.core.event.Message
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Execution metadata produced after handling a message.
 *
 * @param E the type of the message processed.
 * @property event the message instance that was handled.
 * @property duration the time duration taken to process the event.
 * @property parentId the UUID of the parent event in the execution tree, or null if root.
 * @property currentId the UUID assigned to this event execution.
 */
@OptIn(ExperimentalUuidApi::class)
data class PostExecMetadata<E : Message>(
    val event: E,
    val duration: Duration,
    val parentId: Uuid?,
    val currentId: Uuid,
)