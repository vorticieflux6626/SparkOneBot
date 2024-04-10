package com.example.sparkonebot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.ui.text.TextStyle
import com.example.sparkonebot.ui.theme.Navy
import com.example.sparkonebot.ui.theme.Gold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sparkonebot.ui.theme.SparkOneBotTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.Serializable
import java.net.InetAddress
import java.net.SocketTimeoutException

class MainActivity : ComponentActivity() {
    private val apiService = ApiService.create()
    private val coroutineScope = MainScope()
    private val chatState = mutableStateOf(ChatState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SparkOneBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Navy
                ) {
                    ChatScreen(
                        chatState = chatState,
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
                                    handleApiResponse(response)
                                } catch (e: SocketTimeoutException) {
                                    val timeoutMessage = Message(
                                        role = "system",
                                        content = "Socket Timeout Exception"
                                    )
                                    chatState.value = chatState.value.copy(
                                        messages = chatState.value.messages + timeoutMessage
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("chatState", chatState.value)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val savedChatState = savedInstanceState.getSerializable("chatState") as? ChatState
        if (savedChatState != null) {
            chatState.value = savedChatState
        }
    }

    private fun handleApiResponse(response: ApiResponse) {
        if (response.choices.isNotEmpty()) {
            val message = response.choices[0].message
            chatState.value = chatState.value.copy(messages = chatState.value.messages + message)
        }
    }

    private fun pingHost(host: String): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(host)
            inetAddress.isReachable(3000000)
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
        chatState: MutableState<ChatState>,
        onSendPrompt: (String) -> Unit
    ) {
        val scrollState = rememberLazyListState()

        LaunchedEffect(chatState.value.messages.size) {
            if (chatState.value.messages.isNotEmpty()) {
                scrollState.animateScrollToItem(chatState.value.messages.size - 1)
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = scrollState,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(chatState.value.messages) { message ->
                    MessageItem(message)
                }
            }

            Row(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = chatState.value.inputText,
                    onValueChange = { chatState.value = chatState.value.copy(inputText = it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Gold)
                )

                Button(
                    onClick = {
                        val prompt = chatState.value.inputText.trim()
                        if (prompt.isNotEmpty()) {
                            val message = Message("user", prompt)
                            chatState.value = chatState.value.copy(
                                messages = chatState.value.messages + message,
                                inputText = ""
                            )
                            onSendPrompt(prompt)
                        }
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text("Send")
                }
            }

            PingResult(host = "74.137.26.51")
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
        Text(
            text = "${message.role}: ${message.content}",
            color = Gold
        )
    }
}

data class ChatState(
    val messages: List<Message> = emptyList(),
    val inputText: String = ""
) : Serializable

data class Message(
    val role: String,
    val content: String
) : Serializable