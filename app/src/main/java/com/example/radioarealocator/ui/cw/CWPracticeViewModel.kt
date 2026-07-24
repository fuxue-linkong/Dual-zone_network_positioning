package com.example.radioarealocator.ui.cw

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.radioarealocator.data.cw.CWProgress
import com.example.radioarealocator.data.cw.CWProgressStore
import com.example.radioarealocator.data.cw.CWSettings
import com.example.radioarealocator.data.cw.CWSettingsStore
import com.example.radioarealocator.data.cw.CharacterSet
import com.example.radioarealocator.data.cw.MorseCodeGenerator
import com.example.radioarealocator.data.cw.MorseCodePlayer
import com.example.radioarealocator.data.cw.PlayMode
import com.example.radioarealocator.radioApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 使用普通 [ViewModel] + 全局 [radioApp] 上下文（与 [com.example.radioarealocator.ui.MainViewModel] 风格一致）。
 * Navigation3 NavDisplay entry 的 ViewModelStoreOwner 不在 CreationExtras 中提供 APPLICATION_KEY，
 * 改用 AndroidViewModel 会在 viewModel<CWPracticeViewModel>() 处抛 IllegalArgumentException 导致闪退。
 */
class CWPracticeViewModel : ViewModel() {
    private val app = radioApp
    private val settingsStore = CWSettingsStore(app)
    private val progressStore = CWProgressStore(app)
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

    // ---- 教程模式状态 ----
    // currentCourseId: 0=自由练习, 1=Koch, 2=字符组, 3=呼号, 4=文本
    private val _currentCourseId = MutableStateFlow(0)
    val currentCourseId: StateFlow<Int> = _currentCourseId.asStateFlow()

    private val _currentLessonId = MutableStateFlow(0)
    val currentLessonId: StateFlow<Int> = _currentLessonId.asStateFlow()

    private val _currentCourseTitle = MutableStateFlow("")
    val currentCourseTitle: StateFlow<String> = _currentCourseTitle.asStateFlow()

    private val _currentLessonInfo = MutableStateFlow("")
    val currentLessonInfo: StateFlow<String> = _currentLessonInfo.asStateFlow()

    private val _courseProgress = MutableStateFlow<Map<Int, Float>>(emptyMap())
    val courseProgress: StateFlow<Map<Int, Float>> = _courseProgress.asStateFlow()

    private val courseNames = mapOf(
        1 to "Koch课程",
        2 to "字符组练习",
        3 to "呼号训练",
        4 to "文本训练"
    )

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

        // 进入 CW 模块时加载各课程进度，供教程列表显示
        loadAllCourseProgress()
    }

    fun updateSettings(settings: CWSettings) {
        _settings.value = settings
        viewModelScope.launch {
            settingsStore.updateSettings(settings)
        }
    }

    fun generatePracticeText(characterSet: CharacterSet, length: Int) {
        // 切回自由练习时清除教程模式残留状态，避免标题/课时信息错乱
        _currentCourseId.value = 0
        _currentLessonId.value = 0
        _currentCourseTitle.value = ""
        _currentLessonInfo.value = ""

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

    /**
     * 检查抄收结果并保存进度。
     *
     * - 自由练习（currentCourseId == 0）：以 courseId=0 存库，不推进课时。
     * - 教程模式（currentCourseId > 0）：以真实 courseId/lessonId 存库，准确率≥80% 时自动推进到下一课。
     */
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

        val courseId = _currentCourseId.value
        val lessonId = _currentLessonId.value
        viewModelScope.launch {
            val progress = CWProgress(
                courseId = courseId,
                lessonId = lessonId,
                completedAt = System.currentTimeMillis(),
                accuracy = accuracy,
                wpm = _settings.value.wpm,
                duration = _settings.value.practiceDuration
            )
            progressStore.insertProgress(progress)
            // 达标后由用户点击"下一课"按钮触发 advanceCourseProgress，
            // 避免成绩刚显示就被清空，用户来不及看到结果
        }
    }

    fun getCharacterSets(): List<CharacterSet> {
        return generator.getCharacterSets()
    }

    // ---- 教程模式方法 ----

    /**
     * 生成教程练习内容。根据 [courseId] 智能定位到下一个未完成的课时。
     */
    fun generateTutorialText(courseId: Int) {
        _currentCourseId.value = courseId
        _currentLessonId.value = 1
        _currentCourseTitle.value = courseNames[courseId] ?: "教程练习"

        viewModelScope.launch {
            val maxCompletedLesson = progressStore.getMaxCompletedLessonId(courseId)
            val nextLesson = if (maxCompletedLesson != null && maxCompletedLesson > 0) {
                val maxLessons = getMaxLessonsForCourse(courseId)
                (maxCompletedLesson + 1).coerceAtMost(maxLessons)
            } else {
                1
            }

            _currentLessonId.value = nextLesson
            val text = generator.getTutorialContent(courseId = courseId, lessonId = nextLesson, length = 25)
            _currentText.value = text
            _morseCode.value = generator.toMorseCode(text)
            _userInput.value = ""
            _accuracy.value = 0f
            updateLessonInfo()
            loadCourseProgress()
        }
    }

    /**
     * 重置指定课程的进度并从第 1 课重新开始。
     */
    fun resetCourseProgress(courseId: Int) {
        _courseProgress.value = _courseProgress.value.toMutableMap().apply {
            put(courseId, 0f)
        }
        generateTutorialText(courseId)
    }

    /**
     * 加载所有课程的完成进度（供教程列表显示进度条）。
     */
    fun loadAllCourseProgress() {
        viewModelScope.launch {
            loadCourseProgress()
        }
    }

    private fun getMaxLessonsForCourse(courseId: Int): Int = when (courseId) {
        1 -> 26 // Koch 课程 26 个字符
        2, 3, 4 -> 10
        else -> 1
    }

    private suspend fun loadCourseProgress() {
        val progressMap = mutableMapOf<Int, Float>()
        for (cid in 1..4) {
            val maxLessons = getMaxLessonsForCourse(cid)
            val completedCount = progressStore.getCompletedLessonCount(cid)
            val progress = (completedCount.toFloat() / maxLessons).coerceIn(0f, 1f)
            progressMap[cid] = progress
        }
        _courseProgress.value = progressMap
    }

    private fun updateLessonInfo() {
        val courseId = _currentCourseId.value
        val lessonId = _currentLessonId.value

        _currentLessonInfo.value = when (courseId) {
            1 -> "Koch课程 第${lessonId}课 - 学习字符: ${generator.getKochLessonChars(lessonId)}"
            2 -> "字符组练习 第${lessonId}组 - 3字符组合"
            3 -> "呼号训练 第${lessonId}组 - 10个呼号"
            4 -> "文本训练 第${lessonId}组 - CW通联文本"
            else -> ""
        }
    }

    /**
     * 教程模式：推进到下一课。由用户点击"下一课"按钮触发。
     * 若已是最后一课则不推进，仅刷新课程进度。
     */
    fun advanceCourseProgress() {
        val courseId = _currentCourseId.value
        val lessonId = _currentLessonId.value

        if (courseId <= 0) return

        val maxLessons = getMaxLessonsForCourse(courseId)

        if (lessonId < maxLessons) {
            _currentLessonId.value = lessonId + 1
            val text = generator.getTutorialContent(
                courseId = courseId,
                lessonId = lessonId + 1,
                length = 25
            )
            _currentText.value = text
            _morseCode.value = generator.toMorseCode(text)
            _userInput.value = ""
            _accuracy.value = 0f
            updateLessonInfo()
        }

        // 重新加载课程进度
        viewModelScope.launch {
            loadCourseProgress()
        }
    }

    override fun onCleared() {
        super.onCleared()
        player.stop()
    }
}
