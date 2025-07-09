package com.ndmatrix.core.metadata

import kotlin.coroutines.CoroutineContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Contains metadata for the current event execution, enabling tracking of
 * nested call hierarchies.
 *
 * @property parentId the UUID of the parent event, or null if this is the root event.
 * @property currentId the UUID of the current event within the execution chain.
 * @property caller the recursive stack of [com.ndmatrix.core.event.Processable.process] objects
 * calls.
 * @property consumer the consumer`s identifier of the event.
 *
 * Stored in the [kotlin.coroutines.CoroutineContext] to propagate identifiers between coroutines.
 */
@OptIn(ExperimentalUuidApi::class)
data class CallMetadata(
    val parentId: Uuid? = null,
    val currentId: Uuid = Uuid.Companion.random(),
    val caller: Caller? = null,
    val consumer: String? = null,
) : CoroutineContext.Element {
    companion object {
        /**
         * Key for storing and retrieving [CallMetadata] from the [CoroutineContext].
         */
        val CallMetadataKey = object : CoroutineContext.Key<CallMetadata> {}
    }

    override val key: CoroutineContext.Key<*>
        /**
         * Returns the key associated with this context element.
         */
        get() = CallMetadataKey
}