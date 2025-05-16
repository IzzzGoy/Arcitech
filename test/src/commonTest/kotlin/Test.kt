import com.ndmatrix.core.event.Message
import com.ndmatrix.core.parameter.ParameterHolder
import com.ndmatrix.test.base.duration
import com.ndmatrix.test.parameter.parameterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.nanoseconds


sealed interface SampleIntents : Message.Intent {
    data object Foo : SampleIntents
    data object Bar : SampleIntents
}

class SampleParameterHolder : ParameterHolder<SampleIntents, String>("test", SampleIntents::class) {
    override suspend fun handle(e: SampleIntents) {
        when (e) {
            SampleIntents.Bar -> update("foo")
            SampleIntents.Foo -> update("bar")
        }
    }
}

class Test {

    @Test
    fun baseTestTask() {
        val sampleParameterHolder = SampleParameterHolder()
        parameterTest(sampleParameterHolder) {
            occurrence {
                sample("bar", "test", "foo")
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
                sample("test", "bar", "foo")
                metrics {
                    duration<SampleIntents.Foo>(min = 1.nanoseconds)
                }
            }
        }.run(SampleIntents.Foo, SampleIntents.Bar)
    }

}