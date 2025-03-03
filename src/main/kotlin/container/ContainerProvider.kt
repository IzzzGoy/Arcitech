package container

interface ContainerProvider{
    fun<E: Message.Event, S: Any, I: Message.Intent>  provide(key: String): Container<E, S, I>
}