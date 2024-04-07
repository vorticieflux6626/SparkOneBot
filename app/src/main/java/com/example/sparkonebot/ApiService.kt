package com.example.sparkonebot

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("/v1/chat/completions")
    suspend fun generateResponse(@Body request: ApiRequest): ApiResponse

    companion object {
        private const val BASE_URL = "http://192.168.1.176:5000/"

        fun create(): ApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
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

data class Message(
    val role: String,
    val content: String
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