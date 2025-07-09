package com.ndmatrix.core.metadata

import com.ndmatrix.core.event.Message
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Additional information about the process of preparation for the implementation of the event.
 *
 * @property parentId the identifier of the event that triggered the current event.
 * @property message the event to execution.
 * @property sender metadata of the sender of this event.
 * */
@OptIn(ExperimentalUuidApi::class)
data class ExecutionMetadata(
    val parentId: Uuid,
    val message: Message,
    val sender: Caller? = null,
)