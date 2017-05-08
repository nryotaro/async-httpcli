package org.nryotaro.nettysample.sample3

import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor
import org.apache.http.nio.IOControl
import org.apache.http.nio.client.HttpAsyncClient
import org.apache.http.nio.client.methods.AsyncByteConsumer
import org.apache.http.nio.protocol.BasicAsyncRequestProducer
import org.apache.http.nio.protocol.HttpAsyncRequestProducer
import org.apache.http.nio.reactor.ConnectingIOReactor
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

class Client(private val cli: CloseableHttpAsyncClient) {
    val log = LoggerFactory.getLogger(Client::class.java)

    init {
        cli.start()
    }
    fun fin() {
        cli.close()
    }

    fun download(cb: Cb): Future<HttpResponse> {
        return cli.execute(HttpGet(cb.url), cb)
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
        val ioReactor: ConnectingIOReactor = DefaultConnectingIOReactor()
        val cm: PoolingNHttpClientConnectionManager  = PoolingNHttpClientConnectionManager(ioReactor)
        cm.maxTotal = 100
        HttpAsyncClients.custom()
                .setConnectionManager(cm)
                .build()

        val  cli = Client(HttpAsyncClients.custom()
                .setConnectionManager(cm)
                .build())

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
