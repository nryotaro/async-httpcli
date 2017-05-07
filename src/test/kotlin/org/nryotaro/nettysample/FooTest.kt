package org.nryotaro.nettysample

import org.asynchttpclient.AsyncCompletionHandler
import org.asynchttpclient.DefaultAsyncHttpClient
import org.asynchttpclient.Response
import org.junit.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.io.File
import java.time.Duration
import java.time.Duration.ofMillis
import java.util.concurrent.CountDownLatch

class FooTest {

    @Test
    fun footest() {
        val i = File(FooTest::class.java.getResource("hoge.txt").toURI()).readLines()
        
        val c: DefaultAsyncHttpClient = DefaultAsyncHttpClient()

        val c1: Flux<String> = Flux.just(*i.toTypedArray())

        val ee: Flux<Response> = c1.doOnNext {
            println(it)
        }.map{"https://www.sec.gov/"+ it}.delayElements(ofMillis(100L)).flatMap { url ->
            Mono.create<Response>{
                c.prepareGet(url).execute(object : AsyncCompletionHandler<Response>() {
                    override fun onCompleted(response: Response): Response {
                        it.success(response)
                        return response
                    }

                    override fun onThrowable(t: Throwable){
                        it.error(t)
                    }
                })
            }
        }

        val f = CountDownLatch(1)
        ee.doOnNext {println(it.uri)  }.doOnComplete {
            f.countDown()
        }.subscribe {  }

        f.await()

    }
}
