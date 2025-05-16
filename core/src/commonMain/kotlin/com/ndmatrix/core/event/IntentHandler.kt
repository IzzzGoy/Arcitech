package com.ndmatrix.core.event

import kotlin.reflect.KClass

/**
 * Marker class for [EventHandler]s that process [Message.Intent] instances.
 *
 * Differentiates intent handlers from general event handlers.
 *
 * @param E the specific subtype of [Message.Intent] this handler processes.
 */
interface IntentHandler<E : Message.Intent> : EventHandler<E>

abstract class PostMetadataIntentHandler<E : Message.Intent>(
    messageType: KClass<E>
): PostMetadataEventHandler<E>(messageType), IntentHandler<E>