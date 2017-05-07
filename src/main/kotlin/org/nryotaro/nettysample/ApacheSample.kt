package org.nryotaro.nettysample

import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.nio.client.HttpAsyncClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.lang.Exception
import java.time.Duration

class ApacheSample {

    fun a() {
        val cli = HttpAsyncClients.createDefault()

        cli.start()

        Flux.just(*File("/Users/nryotaro/hoge.txt")
                .readLines().map { "https://www.sec.gov" + it}


                .toTypedArray()).delayElements(Duration.ofMillis(100L)).flatMap { url ->
            val b = RequestConfig.custom().setConnectTimeout(3).build()
            HttpGet().config
            Mono.create<Pair<String, HttpResponse>> {
                val g = HttpGet(url)
                g.config = b
                cli.execute(g, object: FutureCallback<HttpResponse> {
                    override fun cancelled() {
                        it.error(RuntimeException("canceled"))
                    }

                    override fun completed(response: HttpResponse) {

                        //response.entity.content.use { it.copyTo() }
                        it.success(Pair(url, response))
                    }

                    override fun failed(e: Exception) {
                        it.error(e)
                    }
                })
            }
        }.doOnNext {
            println("success: -->" + it.first)
        }.blockLast()

    }

}
fun main(args : Array<String>) {
    ApacheSample().a()
    println("Hello Kotlin!!")
}
