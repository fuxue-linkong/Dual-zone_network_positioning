package com.example.radioarealocator.ui.cw

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.radioarealocator.data.cw.CWProgress
import com.example.radioarealocator.data.cw.CWProgressStore
import com.example.radioarealocator.data.cw.CWSettings
import com.example.radioarealocator.data.cw.CWSettingsStore
import com.example.radioarealocator.data.cw.CharacterSet
import com.example.radioarealocator.data.cw.MorseCodeGenerator
import com.example.radioarealocator.data.cw.MorseCodePlayer
import com.example.radioarealocator.data.cw.PlayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CWPracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = CWSettingsStore(application)
    private val progressStore = CWProgressStore(application)
    private val generator = MorseCodeGenerator()
    private val player = MorseCodePlayer()

    private val _settings = MutableStateFlow(CWSettings())
    val settings: StateFlow<CWSettings> = _settings.asStateFlow()

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _morseCode = MutableStateFlow("")
    val morseCode: StateFlow<String> = _morseCode.asStateFlow()

    private val _userInput = MutableStateFlow("")
    val userInput: StateFlow<String> = _userInput.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _accuracy = MutableStateFlow(0f)
    val accuracy: StateFlow<Float> = _accuracy.asStateFlow()

    private val _totalPracticeTime = MutableStateFlow(0)
    val totalPracticeTime: StateFlow<Int> = _totalPracticeTime.asStateFlow()

    private val _averageAccuracy = MutableStateFlow(0f)
    val averageAccuracy: StateFlow<Float> = _averageAccuracy.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settingsFlow.collect { settings ->
                _settings.value = settings
            }
        }

        viewModelScope.launch {
            progressStore.totalPracticeTime.collect { time ->
                _totalPracticeTime.value = time ?: 0
            }
        }

        viewModelScope.launch {
            progressStore.averageAccuracy.collect { accuracy ->
                _averageAccuracy.value = accuracy ?: 0f
            }
        }
    }

    fun updateSettings(settings: CWSettings) {
        _settings.value = settings
        viewModelScope.launch {
            settingsStore.updateSettings(settings)
        }
    }

    fun generatePracticeText(characterSet: CharacterSet, length: Int) {
        val text = generator.generateRandomCharacters(characterSet, length)
        _currentText.value = text
        _morseCode.value = generator.toMorseCode(text)
        // 清空用户输入框，提供干净的学习环境
        _userInput.value = ""
        _accuracy.value = 0f
    }

    fun startPractice() {
        if (_isPlaying.value) return

        _isPlaying.value = true
        _isPaused.value = false
        _userInput.value = ""

        val settings = _settings.value
        player.playMorseCode(
            morseCode = _morseCode.value,
            wpm = settings.wpm,
            frequency = settings.frequency,
            playMode = settings.playMode,
            onComplete = {
                _isPlaying.value = false
            }
        )
    }

    fun pausePractice() {
        if (!_isPlaying.value) return

        _isPaused.value = true
        player.pause()
    }

    fun resumePractice() {
        if (!_isPlaying.value || !_isPaused.value) return

        _isPaused.value = false
        player.resume()
    }

    fun stopPractice() {
        _isPlaying.value = false
        _isPaused.value = false
        player.stop()
    }

    fun updateUserInput(input: String) {
        _userInput.value = input
    }

    fun checkResults() {
        val currentText = _currentText.value
        val userInput = _userInput.value

        if (currentText.isEmpty() || userInput.isEmpty()) {
            _accuracy.value = 0f
            return
        }

        // 大小写不敏感比较：忽略大小写差异，只要字符本身匹配即判定正确。
        // 分母取两者较长长度，多输入/少输入的字符均计为错误
        val correctCount = currentText.zip(userInput).count { (a, b) -> 
            a.equals(b, ignoreCase = true) 
        }
        val maxLen = maxOf(currentText.length, userInput.length)
        val accuracy = correctCount.toFloat() / maxLen.toFloat() * 100f
        _accuracy.value = accuracy

        // 保存进度
        viewModelScope.launch {
            val progress = CWProgress(
                courseId = 0, // 自由练习
                lessonId = 0,
                completedAt = System.currentTimeMillis(),
                accuracy = accuracy,
                wpm = _settings.value.wpm,
                duration = _settings.value.practiceDuration
            )
            progressStore.insertProgress(progress)
        }
    }

    fun getCharacterSets(): List<CharacterSet> {
        return generator.getCharacterSets()
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
    }
}
