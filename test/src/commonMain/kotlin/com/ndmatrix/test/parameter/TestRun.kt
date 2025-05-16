package com.ndmatrix.test.parameter

import com.ndmatrix.core.event.Message
import com.ndmatrix.core.parameter.ParameterHolder
import com.ndmatrix.test.base.SampleStrategy
import com.ndmatrix.test.base.StrategyBuilder
import com.ndmatrix.test.base.TestRun
import com.ndmatrix.test.base.TestRunBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

interface ParameterTestRunBuilder<I : Message.Intent, S : Any> :
    TestRunBuilder<I, S> {

}

internal class ParameterTestRunBuilderImpl<I : Message.Intent, S : Any>(
    private val parameterHolder: ParameterHolder<I, S>
) : ParameterTestRunBuilder<I, S> {

    private lateinit var strategy: SampleStrategy<I, S>

    override fun orderwise(block: StrategyBuilder.Orderwise<I, S>.() -> Unit) {
        strategy = ParameterOrderwiseStrategyBuilderImpl<I, S>(parameterHolder).apply(block).build()
    }

    override fun occurrence(block: StrategyBuilder.Occurrence<I, S>.() -> Unit) {
        strategy = ParameterOccurrenceStrategyBuilderImpl<I, S>(parameterHolder).apply(block).build()
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun build(): TestRun<I> {
        return object : TestRun<I> {
            private val strategy = this@ParameterTestRunBuilderImpl.strategy
            override fun run(vararg event: I) {
                runTest {
                    strategy.run(*event)
                    advanceUntilIdle()
                }
            }
        }
    }

}

fun <I : Message.Intent, S : Any> parameterTest(
    parameterHolder: ParameterHolder<I, S>,
    block: ParameterTestRunBuilder<I, S>.() -> Unit
): TestRun<I> {
    return ParameterTestRunBuilderImpl<I, S>(parameterHolder).apply(block).build()
}