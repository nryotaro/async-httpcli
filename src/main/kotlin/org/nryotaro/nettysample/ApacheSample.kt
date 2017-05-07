package org.nryotaro.nettysample

import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.nio.IOControl
import org.apache.http.nio.client.HttpAsyncClient
import org.apache.http.nio.client.methods.AsyncByteConsumer
import org.apache.http.protocol.HttpContext
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoSink
import sun.misc.IOUtils
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.function.Consumer

class Client {
    val log = LoggerFactory.getLogger(Client::class.java)

    val cli = HttpAsyncClients.createDefault()

    constructor() {
        cli.start()
    }

    fun fin() {
        cli.close()
    }


    fun download(url: String, dest: File): Pair<Future<HttpResponse>, Mono<HttpResponse>> {
        val c = Cb(url, dest)
        val e = Mono.create(c)

        return Pair(cli.execute(HttpGet(url), c), e)
    }
}

class Cb(val url: String, val dest: File): FutureCallback<HttpResponse>, Consumer<MonoSink<HttpResponse>> {
    var sink: MonoSink<HttpResponse>? = null
    override fun accept(t: MonoSink<HttpResponse>) {
        sink = t
    }

    override fun completed(response: HttpResponse) {

        if (dest.exists()) {
            dest.delete()
        } else {
            dest.parentFile.mkdirs()
        }
        dest.createNewFile()

        dest.outputStream().use { out ->
            response.entity.content.use { it ->
                it.copyTo(out)
            }
        }
        println("success: " + url)
        sink?.success(response)
    }

    override fun failed(e: Exception) {
        sink?.error(e)
    }

    override fun cancelled() {
        sink?.error(RuntimeException("canceled"))
    }

}

class ApacheSample {

    val log = LoggerFactory.getLogger(ApacheSample::class.java)
    fun a() {

        val  cli = Client()

        val c = CountDownLatch(1)
        Flux.just(*File("/Users/nryotaro/hoge.txt")
                .readLines()
                .toTypedArray()).delayElements(Duration.ofMillis(100L)).map{ url ->
            cli.download("https://www.sec.gov" + url, File("/tmp/hoge" + url))
        }.flatMap { it.second }
                .doOnNext {
            log.debug(it.toString())
        }.doOnTerminate {
            c.countDown()
        }.subscribe()

        c.await()
        cli.fin()
    }

}
fun main(args : Array<String>) {
    ApacheSample().a()
    println("Hello Kotlin!!")
}
