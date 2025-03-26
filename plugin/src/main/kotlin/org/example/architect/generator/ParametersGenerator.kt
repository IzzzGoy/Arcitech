package org.example.architect.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import container.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.example.architect.models.ConfigSchema
import org.example.architect.models.ProjectionSource
import org.example.architect.models.ProjectionSourceType
import parameter.AbstractEventHandler
import parameter.EventChain
import parameter.ParameterHolder
import parameter.PostExecMetadata
import parameter.Projection
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class ParametersGenerator {
    fun generate(config: ConfigSchema, packageName: String): List<FileSpec> {

        println(buildEventChains(config))
        return config.parameters.map { (classname, definition) ->

            val intentsType = TypeSpec.interfaceBuilder(
                "${classname}Intents"
            )
                .addModifiers(KModifier.SEALED)
                .addSuperinterface(Message.Intent::class)
                .addTypes(
                    definition.intents.map {
                        if (it.value.args == null) {
                            TypeSpec.objectBuilder(it.key)
                        } else {
                            TypeSpec.classBuilder(it.key)
                                .primaryConstructor(
                                    FunSpec.constructorBuilder()
                                        .also { build ->
                                            it.value.args?.forEach { (paramName, def) ->
                                                build.addParameter(
                                                    ParameterSpec.builder(
                                                        paramName,
                                                        castType(def.type)
                                                    )
                                                        .build()
                                                )
                                            }
                                        }
                                        .build()
                                )
                                .addProperties(
                                    it.value.args?.map { (paramName, def) ->
                                        PropertySpec.builder(paramName, castType(def.type))
                                            .initializer(paramName)
                                            .build()
                                    } ?: emptyList()
                                )
                        }
                            .addModifiers(KModifier.DATA)
                            .addSuperinterface(ClassName(packageName, "${classname}Intents"))
                            .build()
                    }
                )
                .build()

            FileSpec.builder(packageName, classname)
                .addType(intentsType)
                .addType(
                    TypeSpec.classBuilder("${classname}ParameterHolder")
                        .addSuperclassConstructorParameter(
                            if (definition.type == "string") {
                                "\"${definition.initial}\""
                            } else {
                                definition.initial
                            }
                        )
                        .addModifiers(KModifier.ABSTRACT)
                        .superclass(
                            ParameterHolder::class.asTypeName().parameterizedBy(
                                ClassName(packageName, "${classname}Intents"),
                                castType(definition.type).asTypeName(),
                            )
                        ).addFunctions(
                            definition.intents.keys.map { intentName ->
                                FunSpec.builder("handle$intentName")
                                    .addModifiers(KModifier.ABSTRACT, KModifier.PROTECTED)
                                    .returns(castType(definition.type))
                                    .addParameter(
                                        ParameterSpec
                                            .builder(
                                                "intent",
                                                ClassName(
                                                    packageName,
                                                    "${classname}Intents.$intentName"
                                                )
                                            )
                                            .build()
                                    )
                                    .addParameter(
                                        "state", castType(definition.type)
                                    )
                                    .build()
                            }
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "_postMetadata",
                                MutableSharedFlow::class.asTypeName()
                                    .parameterizedBy(
                                        PostExecMetadata::class.asTypeName()
                                            .parameterizedBy(
                                                ClassName(packageName, "${classname}Intents")
                                            )
                                    )
                            )
                                .initializer("MutableSharedFlow()")
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "postMetadata",
                                Flow::class.asTypeName()
                                    .parameterizedBy(
                                        PostExecMetadata::class.asTypeName()
                                            .parameterizedBy(
                                                ClassName(packageName, "${classname}Intents")
                                            )
                                    )
                            ).addModifiers(KModifier.OVERRIDE)
                                .initializer("_postMetadata")
                                .build()
                        )
                        .addFunction(
                            FunSpec.builder("handle")
                                .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                                .addParameter(
                                    ParameterSpec.builder(
                                        "e",
                                        ClassName(packageName, "${classname}Intents")
                                    )
                                        .build()
                                )
                                .beginControlFlow("when(e)")
                                .addCode(
                                    definition.intents.map { (intent, name) ->
                                        "is ${classname}Intents.$intent -> update(handle${intent}(e, value))"
                                    }.joinToString(separator = "\n")
                                )
                                .endControlFlow()
                                .build()

                        )
                        .addFunction(
                            FunSpec.builder("process")
                                .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
                                .addParameter(
                                    ParameterSpec.builder("e", Message::class)
                                        .build()
                                )
                                .addCode(
                                    CodeBlock.builder()
                                        .beginControlFlow("if (e is ${classname}Intents)")
                                        .addStatement(
                                            "%M {",
                                            MemberName("kotlin.time", "measureTime")
                                        )
                                        .indent()
                                        .addStatement("handle(e)")
                                        .unindent()
                                        .addStatement("}.also {")
                                        .indent()
                                        .addStatement(
                                            "_postMetadata.emit(%M(e, it))",
                                            MemberName("parameter", "PostExecMetadata")
                                        )
                                        .unindent()
                                        .addStatement("}")
                                        .endControlFlow()
                                        .build()
                                )

                                .build()
                        )
                        .build()
                )
                .build()
        } + generateProjections(
            config,
            packageName
        ) + config.events.map { (eventName, definition) ->
            FileSpec.builder(
                packageName, "Event$eventName"
            )
                .addType(
                    if (definition.args == null) {
                        TypeSpec.objectBuilder(
                            "Event$eventName",
                        )
                            .addSuperinterface(Message.Event::class)
                    } else {
                        TypeSpec.classBuilder(
                            "Event$eventName",
                        )
                            .addSuperinterface(Message.Event::class)
                            .primaryConstructor(
                                FunSpec.constructorBuilder()
                                    .addParameters(
                                        definition.args.map { (paramName, def) ->
                                            ParameterSpec.builder(
                                                paramName,
                                                castType(def.type)
                                            )
                                                .build()
                                        }
                                    )
                                    .build()
                            )
                            .addProperties(
                                definition.args.map { (paramName, def) ->
                                    PropertySpec.builder(paramName, castType(def.type))
                                        .initializer(paramName)
                                        .build()
                                }
                            )
                    }
                        .addModifiers(KModifier.DATA)
                        .build()
                )
                .addType(
                    TypeSpec.classBuilder(
                        "Event${eventName}Handler"
                    )
                        .addModifiers(KModifier.ABSTRACT)
                        .superclass(
                            AbstractEventHandler::class.asTypeName()
                                .parameterizedBy(ClassName(packageName, "Event${eventName}"))
                        )
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter(
                                    ParameterSpec.builder(
                                        "coroutineContext",
                                        CoroutineContext::class
                                    ).build()
                                ).build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "_postMetadata",
                                MutableSharedFlow::class.asTypeName()
                                    .parameterizedBy(
                                        PostExecMetadata::class.asTypeName()
                                            .parameterizedBy(
                                                ClassName(packageName, "Event$eventName")
                                            )
                                    )
                            )
                                .initializer("MutableSharedFlow()")
                                .build()
                        )
                        .addProperty(
                            PropertySpec.builder(
                                "postMetadata",
                                Flow::class.asTypeName()
                                    .parameterizedBy(
                                        PostExecMetadata::class.asTypeName()
                                            .parameterizedBy(
                                                ClassName(packageName, "Event$eventName")
                                            )
                                    )
                            ).addModifiers(KModifier.OVERRIDE)
                                .initializer("_postMetadata")
                                .build()
                        )
                        .addSuperclassConstructorParameter("coroutineContext")
                        .addFunction(
                            FunSpec.builder("process")
                                .addModifiers(KModifier.SUSPEND, KModifier.OVERRIDE)
                                .addParameter(
                                    ParameterSpec.builder("e", Message::class)
                                        .build()
                                )
                                .addCode(
                                    CodeBlock.builder()
                                        .beginControlFlow("if (e is Event${eventName})")
                                        .addStatement(
                                            "%M {",
                                            MemberName("kotlin.time", "measureTime")
                                        )
                                        .indent()
                                        .addStatement("handle(e)")
                                        .unindent()
                                        .addStatement("}.also {")
                                        .indent()
                                        .addStatement(
                                            "_postMetadata.emit(%M(e, it))",
                                            MemberName("parameter", "PostExecMetadata")
                                        )
                                        .unindent()
                                        .addStatement("}")
                                        .endControlFlow()
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        } + config.general.map {
            FileSpec.builder(packageName, "${it}Chain")
                .addType(
                    TypeSpec.classBuilder("${it}Chain")
                        .addModifiers(KModifier.ABSTRACT)
                        .superclass(
                            EventChain::class.asTypeName()
                                .parameterizedBy(ClassName(packageName, "Event$it"))
                        )
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter("coroutineContext", CoroutineContext::class)
                                .addParameters(
                                    buildEventChains(config)
                                        .first { l -> l.first().endsWith(it) }
                                        .filter { it.startsWith("Event") }
                                        .map {
                                            it.split(".")[1]
                                        }
                                        .map {
                                            ParameterSpec.builder(
                                                "${it}Handler",
                                                ClassName(packageName, "Event${it}Handler")
                                            ).build()
                                        }
                                )
                                .addParameters(
                                    buildEventChains(config)
                                        .first { l -> l.first().endsWith(it) }
                                        .filter { !it.startsWith("Event") }
                                        .map {
                                            it.split(".")[0]
                                        }
                                        .map {
                                            ParameterSpec.builder(
                                                "${it}ParameterHolder",
                                                ClassName(packageName, "${it}ParameterHolder")
                                            ).build()
                                        }
                                )
                                .addParameter(
                                    ParameterSpec.builder("isDebug", Boolean::class)
                                        .defaultValue("true").build()
                                )
                                .build()
                        )
                        .addSuperclassConstructorParameter("coroutineContext = coroutineContext")
                        .addSuperclassConstructorParameter("isDebug = isDebug")
                        .addSuperclassConstructorParameter("intentsHandlers = listOf(${
                            buildEventChains(config)
                                .first { l -> l.first().endsWith(it) }
                                .filter { !it.startsWith("Event") }
                                .joinToString(", ") {
                                    it.split(".")[0] + "ParameterHolder"
                                }
                        })"
                        )
                        .addSuperclassConstructorParameter(
                            "eventsSender = listOf(${
                                buildEventChains(config)
                                    .first { l -> l.first().endsWith(it) }
                                    .filter { it.startsWith("Event") }
                                    .joinToString(", ") {
                                        "${it.split(".")[1]}Handler"
                                    }
                            })"
                        )
                        .build()
                )
                .build()
        }
    }

    private fun generateProjections(
        config: ConfigSchema,
        packageName: String
    ) = config.projection.map { (name, params) ->

        FileSpec.builder(packageName, "Projection$name")
            .addType(
                TypeSpec.classBuilder("ProjectionModel$name")
                    .addModifiers(KModifier.DATA)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameters(
                                params.map {
                                    ParameterSpec.builder(
                                        it.name,
                                        extractModelClass(it, config, packageName)
                                    ).defaultValue(
                                        extractDefaultValue(it, config)
                                    ).build()
                                }
                            )
                            .build()
                    )
                    .addProperties(
                        params.map { s ->
                            PropertySpec.builder(s.name, extractModelClass(s, config, packageName))
                                .initializer(s.name)
                                .build()
                        }
                    )
                    .build()
            )
            .addType(
                TypeSpec.classBuilder("Projection$name")
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameters(
                                params.map {
                                    ParameterSpec.builder(
                                        it.name, ClassName(
                                            packageName,
                                            if (it.type == ProjectionSourceType.Param) "${it.name}ParameterHolder" else "Projection${it.name}"
                                        )
                                    )
                                        .build()
                                }
                            )
                            .addParameter("coroutineContext", CoroutineContext::class)
                            .build()
                    )
                    .addModifiers(KModifier.ABSTRACT)
                    .superclass(
                        Projection::class.asTypeName().parameterizedBy(
                            ClassName(packageName, "ProjectionModel$name")
                        )
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "flow",
                            StateFlow::class.asTypeName().parameterizedBy(
                                ClassName(packageName, "ProjectionModel$name")
                            )
                        )
                            .addModifiers(KModifier.OVERRIDE)
                            .initializer(
                                CodeBlock.builder()
                                    .addStatement(
                                        "%M(",
                                        MemberName("kotlinx.coroutines.flow", "combine")
                                    )
                                    .indent()
                                    .addStatement(
                                        params.joinToString(", ") { "${it.name}.flow" }
                                    )
                                    .unindent()
                                    .addStatement(") { ${params.indices.joinToString(separator = ", ") { "t$it" }} ->")
                                    .indent()
                                    .addStatement("project(${params.indices.joinToString(separator = ", ") { "t$it" }})")
                                    .unindent()
                                    .addStatement("}")
                                    .addStatement(
                                        ".%M(",
                                        MemberName("kotlinx.coroutines.flow", "stateIn")
                                    )
                                    .indent()
                                    .addStatement("initialValue = ProjectionModel$name(),")
                                    .addStatement(
                                        "started = %M.Eagerly,",
                                        MemberName("kotlinx.coroutines.flow", "SharingStarted")
                                    )
                                    .addStatement(
                                        "scope = %M(coroutineContext),",
                                        MemberName("kotlinx.coroutines", "CoroutineScope"),
                                    )
                                    .unindent()
                                    .addStatement(")")
                                    .build()
                            )
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("project")
                            .addModifiers(KModifier.ABSTRACT)
                            .addParameters(
                                params.map {
                                    ParameterSpec.builder(
                                        it.name,
                                        extractModelClass(it, config, packageName)
                                    ).build()
                                }
                            )
                            .returns(ClassName(packageName, "ProjectionModel$name"))
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "value",
                            ClassName(packageName, "ProjectionModel$name")
                        )
                            .getter(
                                FunSpec.getterBuilder()
                                    .addCode("return flow.value")
                                    .build()
                            )
                            .addModifiers(KModifier.OVERRIDE)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun extractDefaultValue(
        it: ProjectionSource,
        config: ConfigSchema
    ) = if (it.type == ProjectionSourceType.Param) {
        extractParamDefaultValue(config, it)
    } else {
        "ProjectionModel${it.name}()"
    }

    private fun extractParamDefaultValue(
        config: ConfigSchema,
        it: ProjectionSource
    ) = if (config.parameters[it.name]!!.type == "string") {
        "\"${config.parameters[it.name]!!.initial}\""
    } else {
        config.parameters[it.name]!!.initial
    }

    private fun extractModelClass(
        it: ProjectionSource,
        config: ConfigSchema,
        packageName: String
    ) = if (it.type == ProjectionSourceType.Param) {
        castType(config.parameters[it.name]!!.type).asTypeName()
    } else {
        ClassName(packageName, "ProjectionModel${it.name}")
    }
}

fun castType(type: String) = when (type) {
    "integer" -> Int::class
    "string" -> String::class
    "boolean" -> Boolean::class
    "double" -> Double::class
    "long" -> Long::class
    else -> throw IllegalArgumentException()
}

fun buildEventChains(metadata: ConfigSchema): List<List<String>> {
    val chains = mutableListOf<List<String>>()

    // Для каждого события строим цепочки
    metadata.events.keys.forEach { eventName ->
        buildChainsRecursive(eventName, metadata, mutableListOf(), chains)
    }

    return chains
}

private fun buildChainsRecursive(
    currentHandler: String,
    metadata: ConfigSchema,
    currentChain: MutableList<String>,
    resultChains: MutableList<List<String>>
) {


    // Если это событие (Event), обрабатываем его returns
    if (currentHandler in metadata.events) {
        currentChain.add("Event.$currentHandler")
        val event = metadata.events[currentHandler]!!
        event.returns.forEach { handler ->
            buildChainsRecursive(handler.name, metadata, currentChain.toMutableList(), resultChains)
        }
    }
    // Если это intent (Param), цепочка завершена
    else {
        currentChain.add(currentHandler)
        resultChains.add(currentChain)
    }
}