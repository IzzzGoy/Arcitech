package com.ndmatrix.core.metadata

import kotlinx.coroutines.flow.SharedFlow

/**
 * Provides access to the post-execution metadata flow.
 */
interface PostMetadataProvider {
    /**
     * Public shared flow emitting metadata
     */
    val postMetadata: SharedFlow<PostExecMetadata<*>>
}