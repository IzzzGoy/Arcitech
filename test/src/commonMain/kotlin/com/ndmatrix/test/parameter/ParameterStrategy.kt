package com.ndmatrix.test.parameter

import com.ndmatrix.core.event.Message
import com.ndmatrix.core.metadata.CallMetadata
import com.ndmatrix.core.metadata.PostExecMetadata
import com.ndmatrix.core.parameter.ParameterHolder
import com.ndmatrix.test.base.DefaultStrategyBuilder
import com.ndmatrix.test.base.Metrics
import com.ndmatrix.test.base.SampleStrategy
import com.ndmatrix.test.base.StrategyBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class ParameterOrderwiseStrategy<I : Message.Intent, S : Any>(
    private val parameter: ParameterHolder<I, S>,
    private val metrics: Metrics,
    override val sample: Iterable<S>,
) : SampleStrategy.Orderwise<I, S> {

    private val params = mutableListOf<S>()
    var postMetadata = emptyList<PostExecMetadata<*>>()
    @OptIn(ExperimentalUuidApi::class)
    var parentId: Uuid? = null

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun run(vararg input: I) {


        CoroutineScope(Dispatchers.Default).launch(Dispatchers.Default) {
            postMetadata = parameter
                .postMetadata
                .take(input.size)
                .toList()
        }

        params += parameter.flow.value
        for (event in input) {
            withContext(CallMetadata(parentId)) {
                parameter.process(event)
                params += parameter.flow.value
                parentId = coroutineContext[CallMetadata.CallMetadataKey]?.currentId
            }
        }

        assertEquals(sample.toList(), params.toList())
        postMetadata.forEach {
            metrics.check(it.event::class, it.duration)
            val parent = postMetadata.find { p -> p.currentId == it.parentId }
            if (parent != null) {
                metrics.check(it.event::class, parent.event::class)
            }
        }

    }
}

internal class ParameterOccurrenceStrategy<I : Message.Intent, S : Any>(
    private val parameter: ParameterHolder<I, S>,
    private val metrics: Metrics,
    override val sample: Iterable<S>,
) : SampleStrategy.Occurrence<I, S> {

    private val params = mutableListOf<S>()


    @OptIn(ExperimentalUuidApi::class)
    override suspend fun run(vararg input: I) {

        var postMetadata = emptyList<PostExecMetadata<*>>()
        var parentId: Uuid? = null
        CoroutineScope(Dispatchers.Default).launch(Dispatchers.Default) {
            postMetadata = parameter
                .postMetadata
                .take(input.size)
                .toList()
        }

        params += parameter.flow.value
        for (event in input) {
            withContext(CallMetadata(parentId)) {
                parameter.process(event)
                params += parameter.flow.value
                parentId = coroutineContext[CallMetadata.CallMetadataKey]?.currentId
            }
        }
        params.forEach { param ->
            assertContains(sample, param)
        }
        postMetadata.forEach {
            metrics.check(it.event::class, it.duration)
            val parent = postMetadata.find { p -> p.currentId == it.parentId }
            if (parent != null) {
                metrics.check(it.event::class, parent.event::class)
            }
        }
    }
}

internal interface ParameterOrderwiseStrategyBuilder<I : Message.Intent, S : Any> :
    StrategyBuilder.Orderwise<I, S>

internal class ParameterOrderwiseStrategyBuilderImpl<I : Message.Intent, S : Any>(
    private val parameterHolder: ParameterHolder<I, S>
) : ParameterOrderwiseStrategyBuilder<I, S>,
    DefaultStrategyBuilder<I, S>(ParameterMetricsBuilderImpl<I, S>()) {


    override fun build(): SampleStrategy<I, S> {
        return ParameterOrderwiseStrategy(
            parameter = parameterHolder,
            metrics = metrics,
            sample = sample.asIterable()
        )
    }
}

interface ParameterOccurrenceStrategyBuilder<I : Message.Intent, S : Any> :
    StrategyBuilder.Occurrence<I, S>

internal class ParameterOccurrenceStrategyBuilderImpl<I : Message.Intent, S : Any>(
    private val parameterHolder: ParameterHolder<I, S>
) : ParameterOccurrenceStrategyBuilder<I, S>,
    DefaultStrategyBuilder<I, S>(ParameterMetricsBuilderImpl<I, S>()) {

    override fun build(): SampleStrategy<I, S> {
        return ParameterOccurrenceStrategy(
            parameter = parameterHolder,
            metrics = metrics,
            sample = sample.asIterable()
        )
    }

}