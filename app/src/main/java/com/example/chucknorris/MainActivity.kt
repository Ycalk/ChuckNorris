package com.example.chucknorris

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import androidx.core.content.edit

data class JokeResponse(val joke: String)

interface ChuckNorrisApi {
    @GET("v1/chucknorris")
    suspend fun getJoke(@Header("X-Api-Key") apiKey: String): JokeResponse
}

object RetrofitClient {
    val api: ChuckNorrisApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.api-ninjas.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChuckNorrisApi::class.java)
    }
}

class DataManager(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveJokes(jokes: List<String>) {
        val json = gson.toJson(jokes)
        prefs.edit {putString("jokes_list", json)}
    }

    fun getJokes(): List<String> {
        val json = prefs.getString("jokes_list", null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}

class JokeViewModel : ViewModel() {
    private var dataManager: DataManager? = null

    private val _jokes = MutableStateFlow<List<String>>(emptyList())
    val jokes: StateFlow<List<String>> = _jokes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun init(context: Context) {
        if (dataManager == null) {
            dataManager = DataManager(context)
            _jokes.value = dataManager!!.getJokes()
        }
    }

    fun fetchJoke() {
        val key = BuildConfig.CHUCK_API_KEY
        if (key.isBlank()) {
            _errorMessage.value = "API ключ не настроен"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = RetrofitClient.api.getJoke(key)
                val updatedList = listOf(response.joke) + _jokes.value
                _jokes.value = updatedList
                dataManager?.saveJokes(updatedList)
            } catch (e: Exception) {
                _errorMessage.value = "Ошибка загрузки: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteJoke(jokeToDelete: String) {
        val updatedList = _jokes.value.filter { it != jokeToDelete }
        _jokes.value = updatedList
        dataManager?.saveJokes(updatedList)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    JokeScreen()
                }
            }
        }
    }
}

@Composable
fun JokeScreen(viewModel: JokeViewModel = viewModel()) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    val jokes by viewModel.jokes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(jokes) { joke ->
                JokeItem(
                    jokeText = joke,
                    onDelete = { viewModel.deleteJoke(joke) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.fetchJoke() },
                enabled = !isLoading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Обновить")
            }

            if (isLoading) {
                Spacer(modifier = Modifier.width(16.dp))
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun JokeItem(jokeText: String, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = jokeText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Удалить шутку",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}