package com.example.sparkonebot

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.example.sparkonebot.SparkOneBrain

interface ApiService {
    @POST("/v1/chat/completions")
    suspend fun generateResponse(@Body request: ApiRequest): ApiResponse

    companion object {
        private const val BASE_URL = "http://$SparkOneBrain:5000/"

        fun create(): ApiService {
            val client = OkHttpClient.Builder()
                .connectTimeout(600, TimeUnit.SECONDS) // Increase the connect timeout
                .readTimeout(600, TimeUnit.SECONDS) // Increase the read timeout
                .writeTimeout(600, TimeUnit.SECONDS) // Increase the write timeout
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
            return retrofit.create(ApiService::class.java)
        }
    }
}

data class ApiRequest(
    val messages: List<Message>,
    val mode: String = "instruct",
    val instruction_template: String = "Alpaca"
)

data class ApiResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>,
    val usage: Usage
)

data class Choice(
    val index: Int,
    val finish_reason: String,
    val message: Message
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)