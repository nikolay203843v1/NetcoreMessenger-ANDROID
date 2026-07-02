package com.netcoremessenger.core.di

import com.netcoremessenger.core.data.remote.api.AuthApi
import com.netcoremessenger.core.data.remote.api.ChatsApi
import com.netcoremessenger.core.data.remote.api.MediaApi
import com.netcoremessenger.core.data.remote.api.MessagesApi
import com.netcoremessenger.core.data.remote.api.UsersApi
import com.netcoremessenger.core.data.remote.interceptor.AuthInterceptor
import com.netcoremessenger.core.data.remote.interceptor.TokenAuthenticator
import com.netcoremessenger.core.util.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    class FlexibleLongAdapter : TypeAdapter<Number>() {
        private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        override fun write(out: JsonWriter, value: Number?) {
            if (value == null) {
                out.nullValue()
            } else {
                out.value(value)
            }
        }

        override fun read(`in`: JsonReader): Number? {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }
            val str = `in`.nextString()
            return try {
                str.toLong()
            } catch (e: NumberFormatException) {
                try {
                    if (str.contains("T")) {
                        var parseStr = str.replace("Z", "")
                        if (parseStr.contains(".")) {
                            parseStr = parseStr.substringBefore(".")
                        }
                        format.parse(parseStr)?.time
                    } else {
                        null
                    }
                } catch (ex: Exception) {
                    null
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .registerTypeAdapter(Long::class.java, FlexibleLongAdapter())
        .registerTypeAdapter(Long::class.javaObjectType, FlexibleLongAdapter())
        .create()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(com.netcoremessenger.core.util.ServerConfig.baseUrlForRetrofit())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUsersApi(retrofit: Retrofit): UsersApi {
        return retrofit.create(UsersApi::class.java)
    }

    @Provides
    @Singleton
    fun provideChatsApi(retrofit: Retrofit): ChatsApi {
        return retrofit.create(ChatsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMessagesApi(retrofit: Retrofit): MessagesApi {
        return retrofit.create(MessagesApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMediaApi(retrofit: Retrofit): MediaApi {
        return retrofit.create(MediaApi::class.java)
    }
}
