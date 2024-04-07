package com.example.sparkonebot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sparkonebot.ui.theme.SparkOneBotTheme
import com.example.sparkonebot.ApiService
import com.example.sparkonebot.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val apiService = ApiService.create()
    private val coroutineScope = MainScope()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SparkOneBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val chatMessages = remember { mutableStateListOf<Message>() }
                    ChatScreen(
                        chatMessages = chatMessages,
                        onSendPrompt = { prompt ->
                            coroutineScope.launch {
                                try {
                                    val apiRequest = ApiRequest(
                                        messages = listOf(
                                            Message(
                                                role = "user",
                                                content = prompt
                                            )
                                        )
                                    )
                                    val response = apiService.generateResponse(apiRequest)
                                    handleApiResponse(response, chatMessages)
                                } catch (e: SocketTimeoutException) {
                                    // Handle the timeout error
                                    // Add a system message to the chat dialog
                                    val timeoutMessage = Message(
                                        role = "system",
                                        content = "Socket Timeout Exception"
                                    )
                                    chatMessages.add(timeoutMessage)
                                }
                            }
                        },
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }

    private fun handleApiResponse(response: ApiResponse, chatMessages: MutableList<Message>) {
        if (response.choices.isNotEmpty()) {
            val message = response.choices[0].message
            chatMessages.add(message)
        }
    }

    private fun pingHost(host: String): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(host)
            inetAddress.isReachable(30000) // 30000 millisecond timeout
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun pingHostAsync(host: String): Boolean {
        return withContext(Dispatchers.IO) {
            pingHost(host)
        }
    }

    @Composable
    fun ChatScreen(
        chatMessages: MutableList<Message>,
        onSendPrompt: (String) -> Unit,
        dateFormat: SimpleDateFormat
    ) {
        val inputText = remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(chatMessages) { message ->
                    MessageItem(message)
                }
            }

            Row(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = inputText.value,
                    onValueChange = { inputText.value = it },
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        val prompt = inputText.value.trim()
                        if (prompt.isNotEmpty()) {
                            val message = Message("user", prompt)
                            chatMessages.add(message)
                            onSendPrompt(prompt)
                            inputText.value = ""
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text("Send")
                }
            }

            // Add the PingResult composable
            PingResult(host = "192.168.1.176") // Replace with your host IP address
        }
    }

    @Composable
    fun PingResult(host: String) {
        val hostReachable = remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                hostReachable.value = pingHostAsync(host)
            }
        }

        // Display the ping result
        Text(
            text = if (hostReachable.value) "Remote Host Reachable" else "Remote Host Unreachable",
            color = if (hostReachable.value) Color.Green else Color.Red,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun MessageItem(message: Message) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "${message.role}: ${message.content}")
    }
}