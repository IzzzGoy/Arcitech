package com.ndmatrix.test.base

import com.ndmatrix.core.event.Message
import kotlin.reflect.KClass
import kotlin.time.Duration

interface Metrics {
    fun check(clazz: KClass<out Message>, duration: Duration)
    fun check(clazz: KClass<out Message>, parent: KClass<out Message>)
}

internal inline fun <reified E : Message> Metrics.check(duration: Duration) =
    check(E::class, duration)

interface MetricsBuilder<I: Message> {
    fun duration(event: KClass<out I>, min: Duration = Duration.ZERO, max: Duration = Duration.INFINITE)
    fun parent(event: KClass<out I>, parent: KClass<out I>)
    fun build(): Metrics
}

inline fun <reified I : Message.Intent> MetricsBuilder<in I>.duration(
    min: Duration = Duration.ZERO,
    max: Duration = Duration.INFINITE
) {
    duration(I::class, min, max)
}

inline fun <reified I: Message, reified C : I, reified P : I> MetricsBuilder<in I>.parent() {
    parent(C::class, P::class)
}