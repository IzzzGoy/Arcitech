package com.ndmatrix.test.projection

import com.ndmatrix.core.event.Message
import com.ndmatrix.test.base.Metrics
import com.ndmatrix.test.base.MetricsBuilder
import kotlin.reflect.KClass
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

interface ProjectionMetricsBuilder<I : Message.Intent, S : Any?> : MetricsBuilder<I>

internal class ProjectionMetricsBuilderImpl<I : Message.Intent, S : Any?> :
    ProjectionMetricsBuilder<I, S> {

    private val durations = mutableMapOf<KClass<out Message>, ClosedRange<Duration>>()
    private val parents = mutableMapOf<KClass<out Message>, KClass<out Message>>()

    override fun duration(event: KClass<out I>, min: Duration, max: Duration) {
        durations[event] = min..max
    }

    override fun parent(event: KClass<out I>, parent: KClass<out I>) {
        assertNotEquals(event, parent, "Event ${event.simpleName} can`t be own parent! Check test definitions!")
        parents[event] = parent
    }

    override fun build(): Metrics {
        return object : Metrics {
            private val durations: Map<KClass<out Message>, ClosedRange<Duration>> =
                this@ProjectionMetricsBuilderImpl.durations

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

            override fun check(clazz: KClass<out Message>, parent: KClass<out Message>) {
                val _parent = parents[clazz]
                if (_parent != null) {
                    assertTrue(
                        "Parent event for ${clazz.simpleName} must be ${parent.simpleName}"
                    ) {
                        parent.isInstance(parent)
                    }
                }
            }
        }
    }

}