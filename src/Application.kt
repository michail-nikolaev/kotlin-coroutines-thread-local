package bug.reproduce

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.BaseApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext
import kotlin.random.Random

private var requestIdThreadLocal = ThreadLocal<Int?>()

suspend fun <T> withRequestId(requestId: Int, code: suspend CoroutineScope.() -> T): T {
    if (requestIdThreadLocal.get() != null) {
        throw IllegalStateException("thread local is set somehow?")
    }
    val result =
        withContext(requestIdThreadLocal.asContextElement(requestId)) {
            code()
        }
    if (requestIdThreadLocal.get() != null) {
        // https://github.com/Kotlin/kotlinx.coroutines/issues/985#issuecomment-534929877
        throw IllegalStateException("Oh, it should be fixed in kotlin already! $requestId ${requestIdThreadLocal.get()}")
    }
    return result
}

class RequestContextKtorFeature(
    private val filters: List<(ApplicationCall) -> Boolean>
) {
    class Configuration {
        internal val filters = mutableListOf<(ApplicationCall) -> Boolean>()
        fun filter(predicate: (ApplicationCall) -> Boolean) {
            filters.add(predicate)
        }
    }

    companion object Feature : BaseApplicationPlugin<Application, Configuration, RequestContextKtorFeature> {
        override val key: AttributeKey<RequestContextKtorFeature> = AttributeKey("RequestContext")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): RequestContextKtorFeature {
            val configuration = Configuration().apply(configure)
            val feature = RequestContextKtorFeature(configuration.filters)

            val requestContextPhase = PipelinePhase("RequestId")
            pipeline.insertPhaseAfter(ApplicationCallPipeline.Setup, requestContextPhase)

            pipeline.intercept(requestContextPhase) {
                if (feature.filters.isEmpty() || feature.filters.any { it(call) }) {
                    withRequestId(Random.nextInt()) {
                        proceed()
                    }
                }
            }
            return feature
        }
    }
}

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    install(RequestContextKtorFeature)

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        get("/broken") {
            val client = HttpClient(CIO)

            val htmlContent = client.request("https://en.wikipedia.org/wiki/Main_Page") {
                method = HttpMethod.Get
            }
            call.respondText(htmlContent.bodyAsText(), contentType = ContentType.Text.Plain)
        }
        get("/works") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }
        get("/also-broken") {
            throw IllegalStateException()
        }
    }
}
