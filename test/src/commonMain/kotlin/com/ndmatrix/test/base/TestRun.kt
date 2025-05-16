package com.ndmatrix.test.base

import com.ndmatrix.core.event.Message

interface TestRun<E : Message> {
    fun run(vararg event: E)
}

interface TestRunBuilder<E : Message, S : Any> {
    fun orderwise(
        block: StrategyBuilder.Orderwise<E, S>.() -> Unit
    )

    fun occurrence(
        block: StrategyBuilder.Occurrence<E, S>.() -> Unit
    )

    fun build(): TestRun<E>
}