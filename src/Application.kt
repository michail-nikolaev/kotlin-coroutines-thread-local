package bug.reproduce

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.CoroutineScope
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

    companion object Feature : ApplicationFeature<Application, Configuration, RequestContextKtorFeature> {
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

    routing {
        get("/broken") {
            val htmlContent = HttpClient().request<String> {
                url("https://en.wikipedia.org/wiki/Main_Page")
                method = HttpMethod.Get
            }
            call.respondText(htmlContent, contentType = ContentType.Text.Plain)
        }
        get("/works") {
            call.respondText("HELLO WORLD!", contentType = ContentType.Text.Plain)
        }
    }
}
