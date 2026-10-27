package pl.ballistics.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _inputs = MutableStateFlow(load())
    val inputs: StateFlow<Inputs> = _inputs.asStateFlow()

    private val _result = MutableStateFlow<CalcResult?>(null)
    val result: StateFlow<CalcResult?> = _result.asStateFlow()

    private var job: Job? = null

    init {
        recalculate(debounce = false)
    }

    fun update(transform: (Inputs) -> Inputs) {
        _inputs.value = transform(_inputs.value)
        prefs.edit().putString(KEY, json.encodeToString(Inputs.serializer(), _inputs.value)).apply()
        recalculate(debounce = true)
    }

    private fun recalculate(debounce: Boolean) {
        job?.cancel()
        val snapshot = _inputs.value
        job = viewModelScope.launch {
            if (debounce) delay(250)
            _result.value = withContext(Dispatchers.Default) { Calculator.calculate(snapshot) }
        }
    }

    private fun load(): Inputs = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(Inputs.serializer(), it) }.getOrNull() }
        ?: Inputs()

    private companion object {
        const val KEY = "inputs"
    }
}
