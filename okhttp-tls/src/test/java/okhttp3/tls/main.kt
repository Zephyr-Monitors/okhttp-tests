package okhttp3.tls

import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.OkHttpDebugLogging
import okhttp3.Request
import okhttp3.logging.LoggingEventListener
import java.util.concurrent.TimeUnit

fun main() {
//  OkHttpDebugLogging.enableHttp2()

  val mainThread = Thread.currentThread()

  val client = OkHttpClient().newBuilder()
    .connectTimeout(2000, TimeUnit.MILLISECONDS)
    .readTimeout(2000, TimeUnit.MILLISECONDS)
    .writeTimeout(2000, TimeUnit.MILLISECONDS)
    .callTimeout(5000, TimeUnit.MILLISECONDS)
    .followRedirects(false)
    .eventListenerFactory(object : LoggingEventListener.Factory({
//      println(it)
      if (it.contains("canceled")) {
        println(it)
//        mainThread.stackTrace.forEach {
//          println(it)
//        }
      }
    }) {

    })
    .build()

  val request = Request.Builder()
    .url("https://theornateoracle.com/cart.php?action=add&product_id=456")
    .addHeader(
      "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36"
    )
    .cacheControl(CacheControl.FORCE_NETWORK)
    .get()
    .build()

  val call = client.newCall(request)
  println("Executing...")
  try {
    val response = call.execute()
    assert(response.code == 302)
  } finally {
    println("Done")
  }
}
