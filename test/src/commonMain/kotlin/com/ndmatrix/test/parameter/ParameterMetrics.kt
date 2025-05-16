package com.ndmatrix.test.parameter

import com.ndmatrix.core.event.Message
import com.ndmatrix.test.base.Metrics
import com.ndmatrix.test.base.MetricsBuilder
import kotlin.reflect.KClass
import kotlin.test.assertTrue
import kotlin.time.Duration

interface ParameterMetricsBuilder<I : Message.Intent, S : Any?> : MetricsBuilder<I>

internal class ParameterMetricsBuilderImpl<I : Message.Intent, S : Any?> :
    ParameterMetricsBuilder<I, S> {

    private val durations = mutableMapOf<KClass<out Message>, ClosedRange<Duration>>()

    override fun duration(event: KClass<out I>, min: Duration, max: Duration) {
        durations[event] = min..max
    }

    override fun build(): Metrics {
        return object : Metrics {
            private val durations: Map<KClass<out Message>, ClosedRange<Duration>> =
                this@ParameterMetricsBuilderImpl.durations

            override fun check(clazz: KClass<out Message>, duration: Duration) {
                val range = durations[clazz]
                if (range != null) {
                    assertTrue(
                        "Duration on execution ${clazz.simpleName} out of range $range. Actual duration is $duration"
                    ) {
                        duration in range
                    }
                }
            }
        }
    }

}



