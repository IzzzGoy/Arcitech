package container

import kotlin.reflect.KClass

interface ContainerProvider{
    fun<E: Message.Event, S: Any, I: Message.Intent> provide(
        eKClass: KClass<E>,
        sKClass: KClass<S>,
        iKClass: KClass<I>,
        key: String
    ): Container<E, S, I>
}

inline fun<reified E: Message.Event, reified S: Any, reified I: Message.Intent> ContainerProvider.provide(
    key: String
): Container<E, S, I> {
    return this.provide(E::class, S::class, I::class, key)
}