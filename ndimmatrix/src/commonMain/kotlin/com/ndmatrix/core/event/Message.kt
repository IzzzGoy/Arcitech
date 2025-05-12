package com.ndmatrix.core.event

/**
 * Marker interface for all messages within the framework.
 *
 * Messages represent units of information or commands that
 * can be processed by event handlers and parameter holders.
 */
sealed interface Message {
    /**
     * Marker sub-interface for messages that do not modify parameters.
     *
     * These events serve as triggers for starting or branching event chains,
     * and for interactions outside the library's parameter mutation logic.
     */
    interface Event : Message

    /**
     * Marker sub-interface for messages that represent parameter mutations.
     *
     * Intents cannot be used to start an event chain directly;
     * they encapsulate rules for updating parameter state.
     */
    interface Intent : Message
}