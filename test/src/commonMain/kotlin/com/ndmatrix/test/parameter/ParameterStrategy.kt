package com.ndmatrix.test.parameter

import app.cash.turbine.test
import com.ndmatrix.core.event.Message
import com.ndmatrix.core.metadata.CallMetadata
import com.ndmatrix.core.parameter.ParameterHolder
import com.ndmatrix.test.base.Metrics
import com.ndmatrix.test.base.MetricsBuilder
import com.ndmatrix.test.base.SampleStrategy
import com.ndmatrix.test.base.StrategyBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.test.assertContains
import kotlin.test.assertEquals

internal class ParameterOrderwiseStrategy<I : Message.Intent, S : Any>(
    private val parameter: ParameterHolder<I, S>,
    private val metrics: Metrics,
    override val sample: Iterable<S>,
) : SampleStrategy.Orderwise<I, S> {

    private val params = mutableListOf<S>()

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override suspend fun run(vararg input: I) {

        CoroutineScope(Dispatchers.Default).launch(Dispatchers.Default) {
            parameter.postMetadata.collect {
                metrics.check(it.event::class, it.duration)
            }
        }

        withContext(CallMetadata()) {
            parameter.flow.test {
                params += awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            for (event in input) {
                parameter.process(event)
                parameter.flow.test {
                    params += awaitItem()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        assertEquals(sample.toList(), params.toList())


    }
}

internal class ParameterOccurrenceStrategy<I : Message.Intent, S : Any>(
    private val parameter: ParameterHolder<I, S>,
    private val metrics: Metrics,
    override val sample: Iterable<S>,
) : SampleStrategy.Occurrence<I, S> {

    private val params = mutableListOf<S>()


    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override suspend fun run(vararg input: I) {

        CoroutineScope(Dispatchers.Default).launch(Dispatchers.Default) {
            parameter.postMetadata.collect {
                metrics.check(it.event::class, it.duration)
            }
        }

        withContext(CallMetadata()) {
            parameter.flow.test {
                params += awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            for (event in input) {
                parameter.process(event)
                parameter.flow.test {
                    params += awaitItem()
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
        params.forEach { param ->
            assertContains(sample, param)
        }
    }
}

interface ParameterOrderwiseStrategyBuilder<I : Message.Intent, S : Any> :
    StrategyBuilder.Orderwise<I, S>

class ParameterOrderwiseStrategyBuilderImpl<I : Message.Intent, S : Any>(
    private val parameterHolder: ParameterHolder<I, S>
) : ParameterOrderwiseStrategyBuilder<I, S> {

    private lateinit var metrics: Metrics
    private var sample: Iterable<S> = emptyList()

    override fun sample(sample: Iterable<S>) {
        this.sample = sample
    }

    override fun sample(vararg sample: S) {
        this.sample = sample.asIterable()
    }

    override fun build(): SampleStrategy<I, S> {
        return ParameterOrderwiseStrategy(
            parameter = parameterHolder,
            metrics = metrics,
            sample = sample.asIterable()
        )
    }

    override fun metrics(block: MetricsBuilder<I>.() -> Unit) {
        metrics = ParameterMetricsBuilderImpl<I, S>().apply(block).build()
    }

}

interface ParameterOccurrenceStrategyBuilder<I : Message.Intent, S : Any> :
    StrategyBuilder.Occurrence<I, S>

class ParameterOccurrenceStrategyBuilderImpl<I : Message.Intent, S : Any>(
    private val parameterHolder: ParameterHolder<I, S>
) : ParameterOccurrenceStrategyBuilder<I, S> {
    private lateinit var metrics: Metrics
    private var sample: Iterable<S> = emptyList()

    override fun sample(sample: Iterable<S>) {
        this.sample = sample
    }

    override fun sample(vararg sample: S) {
        this.sample = sample.asIterable()
    }

    override fun metrics(block: MetricsBuilder<I>.() -> Unit) {
        metrics = ParameterMetricsBuilderImpl<I, S>().apply(block).build()
    }

    override fun build(): SampleStrategy<I, S> {
        return ParameterOccurrenceStrategy(
            parameter = parameterHolder,
            metrics = metrics,
            sample = sample.asIterable()
        )
    }

}