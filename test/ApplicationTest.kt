package bug.reproduce

import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testWorks() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/works").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }

    @Test
    fun testBroken() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/broken").apply {
                assertEquals(HttpStatusCode.OK, response.status())
            }
        }
    }
}