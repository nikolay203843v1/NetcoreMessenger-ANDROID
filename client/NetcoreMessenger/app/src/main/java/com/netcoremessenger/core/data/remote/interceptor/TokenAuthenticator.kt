package com.netcoremessenger.core.data.remote.interceptor

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenRefresher: AuthTokenRefresher
) : Authenticator {

    override fun authenticate(route: okhttp3.Route?, response: Response): Request? {
        if (response.code != 401) return null

        val accessToken = runBlocking { tokenRefresher.refreshAccessToken() }
        return accessToken?.let {
            response.request.newBuilder()
                .header("Authorization", "Bearer $it")
                .build()
        }
    }
}
