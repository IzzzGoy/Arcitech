package container

import kotlinx.coroutines.flow.StateFlow

interface UpdatableSource<S: Any> {
    val state: StateFlow<S>
    operator fun invoke(value: S)
}