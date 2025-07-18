package com.arkadst.dataaccessnotifier

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "ServerErrorInterceptor"

class ServerErrorInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())

        var retryCount = 0
        while (response.code == 500 && retryCount < 30) {
            Log.d(TAG, "Received 500 response, waiting 20 seconds before retry")
            response.close()
            Thread.sleep(20000) // 20 second delay
            response = chain.proceed(chain.request())
            retryCount++
        }

        if (response.code == 500) {
            Log.e(TAG, "Failed to recover from 500 error after $retryCount retries")
        }
        return response
    }
}