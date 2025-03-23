package org.example.architect.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import container.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.example.architect.models.ConfigSchema
import parameter.ParameterHolder
import parameter.Projection
import kotlin.coroutines.CoroutineContext

class ParametersGenerator {
    fun generate(config: ConfigSchema, packageName: String): List<FileSpec> {



        return config.parameters.map { (classname, definition) ->

            val intentsType = TypeSpec.interfaceBuilder(
                "${classname}Intents"
            )
                .addModifiers(KModifier.SEALED)
                .addSuperinterface(Message.Intent::class)
                .build()

            FileSpec.builder(packageName, classname)
                .addType(intentsType)
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
                                                    ParameterSpec.builder(paramName, castType(def.type))
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
                                    }?: emptyList()
                                )
                        }
                            .addModifiers(KModifier.DATA)
                            .addSuperinterface(ClassName(packageName, "${classname}Intents"))
                            .build()
                    }
                )
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
                                            .builder("intent", ClassName(packageName, intentName))
                                            .build()
                                    )
                                    .addParameter(
                                        "state", castType(definition.type)
                                    )
                                    .build()
                            }
                        )
                        .addFunction(
                            FunSpec.builder("handle")
                               .addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND)
                               .addParameter(
                                    ParameterSpec.builder("e", ClassName(packageName, "${classname}Intents"))
                                       .build()
                                )
                                .beginControlFlow("when(e)")
                                .addCode(
                                    definition.intents.map { (intent, name) ->
                                        "is $intent -> update(handle${intent}(e, value))"
                                    }.joinToString(separator = "\n")
                                )
                                .endControlFlow()
                               .build()

                        )
                        .build()
                )
                .build()
        } + config.projection.map { (name, params) ->

            FileSpec.builder(packageName, "Projection$name")
                .addType(
                    TypeSpec.classBuilder("ProjectionModel$name")
                        .addModifiers(KModifier.DATA)
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                               .addParameters(
                                   params.map {
                                       ParameterSpec.builder(it, castType(config.parameters[it]!!.type))
                                           .defaultValue(
                                               if (config.parameters[it]!!.type == "string") {
                                                   "\"${config.parameters[it]!!.initial}\""
                                               } else {
                                                   config.parameters[it]!!.initial
                                               }
                                           )
                                           .build()
                                   }
                               )
                               .build()
                        )
                        .addProperties(
                            params.map { s ->
                                PropertySpec.builder(s, castType(config.parameters[s]!!.type))
                                    .initializer(s)
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
                                       ParameterSpec.builder(it, ClassName(packageName, "${it}ParameterHolder"))
                                           .build()
                                   }
                               )
                                .addParameter("coroutineContext", CoroutineContext::class)
                               .build()
                        )
                        .addModifiers(KModifier.ABSTRACT)
                        .superclass(Projection::class.asTypeName().parameterizedBy(
                            ClassName(packageName, "ProjectionModel$name")
                        ))
                        .addProperty(
                            PropertySpec.builder(
                                "flow",
                                StateFlow::class.asTypeName().parameterizedBy(
                                    ClassName(packageName, "ProjectionModel$name")
                                )
                            )
                                .addModifiers(KModifier.OVERRIDE)
                                .initializer(
                                    //"MutableStateFlow(ProjectionModel$name())"
                                    CodeBlock.builder()
                                        .addStatement("%M(", MemberName("kotlinx.coroutines.flow", "combine"))
                                        .indent()
                                        .addStatement(
                                            params.joinToString(", ") { "$it.flow" }
                                        )
                                        .unindent()
                                        .addStatement(") { ${params.indices.joinToString(separator = ", ") { "t$it" }} ->")
                                        .indent()
                                        .addStatement("project(${params.indices.joinToString(separator = ", ") { "t$it" }})")
                                        .unindent()
                                        .addStatement("}")
                                        .addStatement(".%M(", MemberName("kotlinx.coroutines.flow", "stateIn"))
                                        .indent()
                                        .addStatement("initialValue = ProjectionModel$name(),")
                                        .addStatement("started = %M.Eagerly,", MemberName("kotlinx.coroutines.flow", "SharingStarted"))
                                        .addStatement("scope = %M(coroutineContext),",
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
                                       ParameterSpec.builder(it, castType(config.parameters[it]!!.type))
                                           .build()
                                   }
                               )
                               .returns(ClassName(packageName, "ProjectionModel$name"))
                               .build()
                        )
                       /* .addProperty(
                            PropertySpec.builder(
                                "flow",
                                StateFlow::class.asTypeName().parameterizedBy(
                                    ClassName(packageName, "ProjectionModel$name")
                                )
                            )
                                .initializer("_flow")
                                .addModifiers(KModifier.OVERRIDE)
                                .build()
                        )*/
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
                        /*.addInitializerBlock(
                            CodeBlock.builder()
                                .beginControlFlow(
                                    "%M(%M.Default).%M",
                                    MemberName("kotlinx.coroutines", "CoroutineScope"),
                                    MemberName("kotlinx.coroutines", "Dispatchers"),
                                    MemberName("kotlinx.coroutines", "launch"),
                                )
                                .addStatement("%M(", MemberName("kotlinx.coroutines.flow", "combine"))
                                .indent()
                                .addStatement(
                                    params.joinToString(", ") { "$it.flow" }
                                )
                                .unindent()
                                .addStatement(") { ${params.indices.joinToString(separator = ", ") { "t$it" }} ->")
                                .indent()
                                .addStatement("_flow.value = project(${params.indices.joinToString(separator = ", ") { "t$it" }})")
                                .unindent()
                                .addStatement("}")
                                .addStatement(".%M()", MemberName("kotlinx.coroutines.flow", "collect"))
                                .endControlFlow()
                                .build()
                        )*/
                        .build()
                )
                .build()
        }
    }
}

fun castType(type: String) = when(type) {
    "integer" -> Int::class
    "string" -> String::class
    "boolean" -> Boolean::class
    "double" -> Double::class
    "long" -> Long::class
    else -> throw IllegalArgumentException()
}