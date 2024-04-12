package com.example.sparkonebot

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.sparkonebot.ui.theme.Gold
import com.example.sparkonebot.ui.theme.Navy
import com.example.sparkonebot.ui.theme.SparkOneBotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.Serializable
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.*
import androidx.compose.material.icons.rounded.Phone

// Global Variables
val MyAppIcons = Icons.Rounded
val hostReachable = mutableStateOf(false)
const val SparkOneBrain: String = "74.137.26.51"

class MainActivity : ComponentActivity() {
    private val apiService = ApiService.create()
    private val coroutineScope = MainScope()
    private val chatState = mutableStateOf(ChatState())
    private var textToSpeech: TextToSpeech? = null
    private val isIntroAnimationFinished = mutableStateOf(false)

    companion object {
        private const val SPEECH_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check host reachability on startup
        coroutineScope.launch {
            hostReachable.value = pingHostAsync(SparkOneBrain)
        }

        textToSpeech = TextToSpeech(this, TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
            }
        })

        if (savedInstanceState != null) {
            isIntroAnimationFinished.value = savedInstanceState.getBoolean("isIntroAnimationFinished", false)
        }

        setContent {
            SparkOneBotTheme {
                MainScreen(chatState, apiService, coroutineScope, isIntroAnimationFinished)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("chatState", chatState.value)
        outState.putBoolean("isIntroAnimationFinished", isIntroAnimationFinished.value)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        val savedChatState = savedInstanceState.getSerializable("chatState") as? ChatState
        if (savedChatState != null) {
            chatState.value = savedChatState
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val spokenText = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            chatState.value = chatState.value.copy(inputText = spokenText ?: "")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    private fun handleApiResponse(response: ApiResponse) {
        if (response.choices.isNotEmpty()) {
            val message = response.choices[0].message
            chatState.value = chatState.value.copy(messages = chatState.value.messages + message)
            speak(message.content)
        }
    }

    private fun speak(text: String) {
        val utteranceId = UUID.randomUUID().toString()
        textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
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
    fun MainScreen(
        chatState: MutableState<ChatState>,
        apiService: ApiService,
        coroutineScope: CoroutineScope,
        isIntroAnimationFinished: MutableState<Boolean>
    ) {
        val backgroundColor = if (isIntroAnimationFinished.value) Navy else Color.Black
        val configuration = LocalConfiguration.current

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            if (!isIntroAnimationFinished.value && configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                IntroScreen(
                    onAnimationFinished = {
                        isIntroAnimationFinished.value = true
                    }
                )
            } else {
                ChatScreen(
                    chatState = chatState,
                    onSendPrompt = { prompt ->
                        coroutineScope.launch {
                            hostReachable.value = pingHostAsync(SparkOneBrain)
                            if (hostReachable.value) {
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
                            } else {
                                val networkErrorMessage = Message(
                                    role = "system",
                                    content = "Network Connectivity Error. Please Check your Internet Connection and Try Again."
                                )
                                chatState.value = chatState.value.copy(
                                    messages = chatState.value.messages + networkErrorMessage
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun ChatScreen(
        chatState: MutableState<ChatState>,
        onSendPrompt: (String) -> Unit
    ) {
        val scrollState = rememberLazyListState()
        val animationState = remember { mutableStateOf(0) }
        val coroutineScope = rememberCoroutineScope()
        val isAnimationVisible = remember { mutableStateOf(false) }

        LaunchedEffect(chatState.value.messages.size) {
            if (chatState.value.messages.isNotEmpty()) {
                val lastMessage = chatState.value.messages.last()
                if (lastMessage.role == "user") {
                    isAnimationVisible.value = true
                } else {
                    isAnimationVisible.value = false
                }
                scrollState.animateScrollToItem(chatState.value.messages.size - 1)
            }
        }

        LaunchedEffect(Unit) {
            while (true) {
                delay(300)
                if (isAnimationVisible.value) {
                    animationState.value = (animationState.value + 1) % 5
                }
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
                    if (message.role == "user" && isAnimationVisible.value) {
                        LoadingAnimation(animationState.value)
                    }
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

                Button(
                    onClick = {
                        // Launch speech recognition
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        intent.putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
                        startActivityForResult(intent, SPEECH_REQUEST_CODE)
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = MyAppIcons.Phone,
                        contentDescription = "Microphone"
                    )
                }
            }

            PingResult(host = SparkOneBrain)
        }
    }

    @Composable
    fun LoadingAnimation(frame: Int) {
        val animationText = remember {
            listOf(
                "Working ....",
                "Working o...",
                "Working .o..",
                "Working ..o.",
                "Working ...o"
            )
        }

        Text(
            text = animationText[frame],
            color = Gold,
            modifier = Modifier.padding(16.dp)
        )
    }

    @Composable
    fun MessageItem(message: Message) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                Text(
                    text = "${message.role}: ",
                    color = Color.Green,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = message.content,
                    color = Gold
                )
            }
        }
    }

    @Composable
    fun PingResult(host: String) {
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            coroutineScope.launch {
                hostReachable.value = pingHostAsync(host)
            }
        }

        Text(
            text = if (hostReachable.value) "SparkOne Brain Online" else "SparkOne Brain Unreachable",
            color = if (hostReachable.value) Color.Green else Color.Red,
            modifier = Modifier.padding(16.dp)
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