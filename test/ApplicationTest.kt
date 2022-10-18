package bug.reproduce

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
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

    @Test
    fun testAlsoBroken() {
        withTestApplication({ module(testing = true) }) {
            handleRequest(HttpMethod.Get, "/also-broken").apply {
                assertEquals(HttpStatusCode.InternalServerError, response.status())
            }
        }
    }
}
