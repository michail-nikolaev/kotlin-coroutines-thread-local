package io.ktor.util.pipeline

import io.ktor.util.cio.readChannel
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNull

private val requestIdThreadLocal = ThreadLocal<Int?>()

class SuspendFunctionGunTest {
    @Test
    fun test() = runBlocking {
        val interceptors = listOf<PipelineInterceptor<Unit, Unit>>(
            {
                withContext(requestIdThreadLocal.asContextElement(123)) {
                    proceed()
                }

                println(requestIdThreadLocal.get())
                assertNull(requestIdThreadLocal.get(), "Thread local's context should be restored")
            },

            {
                // file has more than 4088 bytes
                val channel = File(object {}.javaClass.getResource("/file").file).readChannel()
                val result = ByteArray(4089)
                channel.readFully(result, 0, 4089)
            }
        )

        SuspendFunctionGun(Unit, Unit, interceptors as List<PipelineInterceptorFunction<Unit, Unit>>).execute(Unit)
    }
}
