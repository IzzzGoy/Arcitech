import com.ndmatrix.core.event.Message
import com.ndmatrix.core.parameter.ParameterHolder
import com.ndmatrix.core.parameter.Projection
import com.ndmatrix.test.base.duration
import com.ndmatrix.test.base.parent
import com.ndmatrix.test.parameter.parameterTest
import com.ndmatrix.test.projection.projectionTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.test.Test
import kotlin.time.Duration.Companion.nanoseconds


sealed interface SampleIntents : Message.Intent {
    data object Foo : SampleIntents
    data object Bar : SampleIntents
}

class SampleParameterHolder : ParameterHolder<SampleIntents, String>("test", SampleIntents::class) {
    override suspend fun handle(e: SampleIntents) {
        when (e) {
            SampleIntents.Bar -> update("foo-foo")
            SampleIntents.Foo -> update("bar")
        }
    }
}

class TestProjection(
    parameterHolder: SampleParameterHolder,
) : Projection<Int>(Dispatchers.Default) {
    override val flow: StateFlow<Int> = parameterHolder.flow.map { it.length }.stateIn(
        projectionCoroutineScope,
        initialValue = 0,
        started = SharingStarted.WhileSubscribed(5_000L)
    )

}

class Test {

    @Test
    fun baseTestTask() {
        val sampleParameterHolder = SampleParameterHolder()
        parameterTest(sampleParameterHolder) {
            occurrence {
                sample("bar", "test", "foo-foo")
                metrics {
                    duration<SampleIntents.Foo>(min = 1.nanoseconds)
                }
            }
        }.run(SampleIntents.Foo, SampleIntents.Bar)
    }

    @Test
    fun anotherTestTask() {
        val sampleParameterHolder = SampleParameterHolder()
        parameterTest(sampleParameterHolder) {
            orderwise {
                sample("test", "bar", "foo-foo")
                metrics {
                    duration<SampleIntents.Foo>(min = 1.nanoseconds)
                    parent<SampleIntents, SampleIntents.Foo, SampleIntents.Bar>()
                }
            }
        }.run(SampleIntents.Foo, SampleIntents.Bar)
    }

    @Test
    fun testProjection() {
        val sampleParameterHolder = SampleParameterHolder()
        val testProjection = TestProjection(sampleParameterHolder)
        projectionTest(sampleParameterHolder, testProjection) {
            occurrence {
                sample(0, 7, 3)
                metrics {

                }
            }
        }.run(SampleIntents.Foo, SampleIntents.Bar)
    }

    @Test
    fun testProjectionOrderwise() {
        val sampleParameterHolder = SampleParameterHolder()
        val testProjection = TestProjection(sampleParameterHolder)
        projectionTest(sampleParameterHolder, testProjection) {
            orderwise {
                sample(0, 3, 7)
                metrics {

                }
            }
        }.run(SampleIntents.Foo, SampleIntents.Bar)
    }

}