package org.example.architect.models

import kotlinx.serialization.Serializable

@Serializable
data class ConfigSchema(
    val parameters: Map<String, ParametersDefinition>,
    val projection: Map<String, List<String>>
)

@Serializable
data class ParametersDefinition(
    val type: String,
    val initial: String,
    val intents: Map<String, IntentsDefinition>,
)

@Serializable
data class IntentsDefinition(
    val args: Map<String, ArgDefinition>?
)

@Serializable
data class ArgDefinition(
    val type: String,
)