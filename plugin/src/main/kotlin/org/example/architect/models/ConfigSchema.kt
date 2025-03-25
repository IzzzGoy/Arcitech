package org.example.architect.models

import kotlinx.serialization.Serializable

@Serializable
data class ConfigSchema(
    val parameters: Map<String, ParametersDefinition>,
    val projection: Map<String, List<ProjectionSource>>,
    val events: Map<String, EventDefinition>,
    val general: List<String>
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

@Serializable
data class ProjectionSource(
    val type: ProjectionSourceType,
    val name: String,
)

@Serializable
enum class ProjectionSourceType {
    Param, Projection
}

@Serializable
data class EventDefinition(
    val args: Map<String, ArgDefinition>?,
    val returns: List<ReturnsDefinition>
)

@Serializable
data class ReturnsDefinition(
    val type: EventReturnsType,
    val name: String
)

@Serializable
enum class EventReturnsType {
    Param, Projection
}