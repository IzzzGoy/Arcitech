package com.ndmatrix.test.projection

import com.ndmatrix.core.event.Message
import com.ndmatrix.core.parameter.ParameterHolder
import com.ndmatrix.core.parameter.Projection
import com.ndmatrix.test.base.SampleStrategy
import com.ndmatrix.test.base.StrategyBuilder
import com.ndmatrix.test.base.TestRun
import com.ndmatrix.test.base.TestRunBuilder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

interface ProjectionTestRunBuilder<I : Message.Intent, S : Any> : TestRunBuilder<I, S>


internal class ProjectionTestRunBuilderImpl<I : Message.Intent, S : Any>(
    private val parameterHolder: ParameterHolder<I, *>,
    private val projection: Projection<S>,
) : ProjectionTestRunBuilder<I, S> {

    private lateinit var strategy: SampleStrategy<I, S>

    override fun orderwise(block: StrategyBuilder.Orderwise<I, S>.() -> Unit) {
        strategy = ProjectionOrderwiseStrategyBuilder<I, S>(
            parameterHolder,
            projection
        ).apply(block).build()
    }

    override fun occurrence(block: StrategyBuilder.Occurrence<I, S>.() -> Unit) {
        strategy = ProjectionOccurrenceStrategyBuilder<I, S>(
            parameterHolder,
            projection
        ).apply(block).build()
    }


    @OptIn(ExperimentalCoroutinesApi::class)
    override fun build(): TestRun<I> {
        return object : TestRun<I> {
            private val strategy = this@ProjectionTestRunBuilderImpl.strategy
            override fun run(vararg event: I) {
                runTest {
                    strategy.run(*event)
                    advanceUntilIdle()
                }
            }
        }
    }
}

fun <I : Message.Intent, S : Any> projectionTest(
    parameterHolder: ParameterHolder<I, *>,
    projection: Projection<S>,
    block: ProjectionTestRunBuilder<I, S>.() -> Unit
): TestRun<I> {
    return ProjectionTestRunBuilderImpl<I, S>(parameterHolder, projection).apply(block).build()
}