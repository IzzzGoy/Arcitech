package com.ndmatrix.test.base

import com.ndmatrix.core.event.Message


/**
 * Strategy to check valueble flow of data with given [sample].
 *
 * For parameter/projection observable flow is flow of values.
 * For event-handler/event-chain - flow of output events.
 * */
interface SampleStrategy<I: Message, T : Any?> {

    val sample: Iterable<T>

    interface Orderwise<I: Message, T : Any?> : SampleStrategy<I, T>
    interface Occurrence<I: Message, T : Any?> : SampleStrategy<I, T>

    suspend fun run(vararg input: I)
}

interface StrategyBuilder<I : Message, T : Any?> {

    fun build(): SampleStrategy<I, T>

    fun sample(sample: Iterable<T>)
    fun sample(vararg sample: T)

    fun metrics(block: MetricsBuilder<I>.() -> Unit)

    interface Orderwise<I : Message, T : Any?> : StrategyBuilder<I, T>
    interface Occurrence<I : Message, T : Any?> : StrategyBuilder<I, T>
}

internal abstract class DefaultStrategyBuilder<I: Message, T: Any?>(
    private val metricsBuilder: MetricsBuilder<I>
): StrategyBuilder<I, T> {
    lateinit var metrics: Metrics
        private set
    var sample: Iterable<T> = emptyList()
        private set

    override fun sample(sample: Iterable<T>) {
        this.sample = sample
    }

    override fun sample(vararg sample: T) {
        this.sample = sample.asIterable()
    }

    override fun metrics(block: MetricsBuilder<I>.() -> Unit) {
        metrics = metricsBuilder.apply(block).build()
    }
}