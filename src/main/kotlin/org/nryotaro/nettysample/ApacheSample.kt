package org.nryotaro.nettysample

import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.nio.IOControl
import org.apache.http.nio.client.HttpAsyncClient
import org.apache.http.nio.client.methods.AsyncByteConsumer
import org.apache.http.nio.protocol.BasicAsyncRequestProducer
import org.apache.http.nio.protocol.HttpAsyncRequestProducer
import org.apache.http.protocol.HttpContext
import org.slf4j.LoggerFactory
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.MonoSink
import sun.misc.IOUtils
import java.io.File
import java.lang.Exception
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
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

    fun download(cb: Cb): Future<HttpResponse> {

        val c: HttpAsyncRequestProducer = BasicAsyncRequestProducer(HttpHost("www.sec.gov"), HttpGet(cb.url))
        return cli.execute(c,A(cb.dest), cb)
    }
}

class A(dest: File): AsyncByteConsumer<HttpResponse>() {

    val ch = FileChannel.open(Paths.get(dest.toURI()),StandardOpenOption.WRITE)
    var response: HttpResponse? = null

    override fun onResponseReceived(response: HttpResponse) {
        println("received")
        this.response = response
    }

    override fun onByteReceived(buf: ByteBuffer, p1: IOControl) {
        ch.write(buf)
    }

    override fun buildResult(p0: HttpContext?): HttpResponse? {
        println("build")
        return response
    }

    override fun releaseResources() {
        super.releaseResources()
        ch.close()
    }

}

class Cb(val url: String, val dest: File, val latch: CountDownLatch): FutureCallback<HttpResponse> {

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
        latch.countDown()
    }

    override fun failed(e: Exception) {
        println("failure: " + url)
        latch.countDown()
    }

    override fun cancelled() {
        println("canceled: " + url)
        latch.countDown()

    }

}

class ApacheSample {

    val log = LoggerFactory.getLogger(ApacheSample::class.java)
    fun a() {

        val  cli = Client()

        val e = File("/Users/nryotaro/hoge.txt")
                .readLines()
        val c = CountDownLatch(e.size)

         Flux.just(*e.toTypedArray())
        .delayElements(Duration.ofMillis(100L)).map{ url: String ->
          cli.download(Cb("https://www.sec.gov" + url, File("/tmp/hoge" + url), c))
        }.subscribe()

        c.await()
        cli.fin()
    }

}
fun main(args : Array<String>) {
    ApacheSample().a()
    println("Hello Kotlin!!")
}
