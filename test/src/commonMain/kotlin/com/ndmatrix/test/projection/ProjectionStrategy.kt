package com.ndmatrix.test.projection

import app.cash.turbine.test
import com.ndmatrix.core.event.Message
import com.ndmatrix.core.metadata.CallMetadata
import com.ndmatrix.core.metadata.PostExecMetadata
import com.ndmatrix.core.parameter.ParameterHolder
import com.ndmatrix.core.parameter.Projection
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

internal class ProjectionOrderwiseStrategyBuilder<E: Message.Intent, S: Any?>(
    private val parameter: ParameterHolder<E, *>,
    private val projection: Projection<S>,
): StrategyBuilder.Orderwise<E, S>, DefaultStrategyBuilder<E, S>(ProjectionMetricsBuilderImpl<E, S>()) {
    override fun build(): SampleStrategy<E, S> {
        return ProjectionOrderviseStrategy<E, S>(
            parameter = parameter, projection = projection, sample = sample, metrics = metrics
        )
    }
}

internal class ProjectionOccurrenceStrategyBuilder<E: Message.Intent, S: Any?>(
    private val parameter: ParameterHolder<E, *>,
    private val projection: Projection<S>,
): StrategyBuilder.Occurrence<E, S>, DefaultStrategyBuilder<E, S>(ProjectionMetricsBuilderImpl<E, S>()) {
    override fun build(): SampleStrategy<E, S> {
        return ProjectionOccurrenceStrategy<E, S>(
            parameter = parameter, projection = projection, sample = sample, metrics = metrics
        )
    }
}

internal class ProjectionOrderviseStrategy<E: Message.Intent, S: Any?>(
    private val parameter: ParameterHolder<E, *>,
    private val projection: Projection<S>,
    override val sample: Iterable<S>,
    private val metrics: Metrics,
): SampleStrategy.Orderwise<E, S> {
    private val params = mutableListOf<S>()


    @OptIn(ExperimentalUuidApi::class)
    override suspend fun run(vararg input: E) {

        var postMetadata = emptyList<PostExecMetadata<*>>()
        var parentId: Uuid? = null
        CoroutineScope(Dispatchers.Default).launch(Dispatchers.Default) {
            postMetadata = parameter
                .postMetadata
                .take(input.size)
                .toList()
        }
        projection.flow.test {
            params += awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        for (event in input) {
            withContext(CallMetadata(parentId)) {
                parameter.process(event)
                projection.flow.test {
                    params += awaitItem()
                    cancelAndIgnoreRemainingEvents()
                }
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

internal class ProjectionOccurrenceStrategy<E: Message.Intent, S: Any?>(
    private val parameter: ParameterHolder<E, *>,
    private val projection: Projection<S>,
    override val sample: Iterable<S>,
    private val metrics: Metrics,
): SampleStrategy.Orderwise<E, S> {

    private val params = mutableListOf<S>()


    @OptIn(ExperimentalUuidApi::class)
    override suspend fun run(vararg input: E) {

        var postMetadata = emptyList<PostExecMetadata<*>>()
        var parentId: Uuid? = null
        CoroutineScope(Dispatchers.Default).launch(Dispatchers.Default) {
            postMetadata = parameter
                .postMetadata
                .take(input.size)
                .toList()
        }
        projection.flow.test {
            params += awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        for (event in input) {
            withContext(CallMetadata(parentId)) {
                parameter.process(event)
                projection.flow.test {
                    params += awaitItem()
                    cancelAndIgnoreRemainingEvents()
                }
                parentId = coroutineContext[CallMetadata.CallMetadataKey]?.currentId
            }
        }
        val prettySample = sample.toList()
        params.forEach { param ->
            assertContains(prettySample, param)
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