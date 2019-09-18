package com.asfoundation.wallet.util

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class UserAgentInterceptor(private val userAgent: String) : Interceptor {

  @Throws(IOException::class)
  override fun intercept(chain: Interceptor.Chain): Response {
    val originalRequest = chain.request()
    val requestWithUserAgent = originalRequest.newBuilder()
        .addHeader("User-Agent", userAgent)
        .build()
    return chain.proceed(requestWithUserAgent)
  }
}
