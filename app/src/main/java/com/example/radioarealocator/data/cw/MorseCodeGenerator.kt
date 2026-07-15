package com.example.radioarealocator.data.cw

class MorseCodeGenerator {
    private val morseCodeMap = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '!' to "-.-.--", '-' to "-....-",
        '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-",
        ' ' to "/", '=' to "-...-"
    )

    // Koch方法字符顺序（按难度排列，参考lcwo.net）
    private val kochCharOrder = "KMURESNAPTLWI.JZ=FOY,VG5/Q92H38B?47C1D60X"

    // CW常用词汇
    private val cwCommonWords = listOf(
        "CQ", "DE", "K", "KN", "SK", "AR", "BT", "AS", "VE", "73",
        "RST", "RIG", "ANT", "PWR", "WX", "TEMP", "NAME", "QTH", "QRM", "QRN",
        "QSB", "QSL", "OM", "YL", "ES", "FB", "GM", "GA", "GE",
        "TU", "HI", "HIHI", "TEST", "R", "RR", "UR", "MY", "YOUR"
    )

    fun toMorseCode(text: String): String {
        return text.uppercase().map { char ->
            morseCodeMap[char] ?: ""
        }.filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun generateRandomCharacters(characterSet: CharacterSet, length: Int): String {
        val chars = when (characterSet) {
            CharacterSet.LETTERS -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            CharacterSet.NUMBERS -> "0123456789"
            CharacterSet.SYMBOLS -> ".,?!-/()"
            CharacterSet.CUSTOM -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        }
        return (1..length).map { chars.random() }.joinToString("")
    }

    fun getCharacterSets(): List<CharacterSet> {
        return CharacterSet.values().toList()
    }

    /**
     * Koch课程内容生成
     * @param lesson 课程编号（1-26），每课增加1个新字符
     * @param length 练习文本长度
     * @return 包含已学字符的随机文本
     */
    fun generateKochLesson(lesson: Int, length: Int = 25): String {
        val charCount = lesson.coerceIn(1, 26)
        val availableChars = kochCharOrder.take(charCount)
        return (1..length).map { availableChars.random() }.joinToString("")
    }

    /**
     * 获取Koch课程可用字符
     * @param lesson 课程编号
     * @return 当前课程可用的字符集
     */
    fun getKochLessonChars(lesson: Int): String {
        val charCount = lesson.coerceIn(1, 26)
        return kochCharOrder.take(charCount)
    }

    /**
     * 字符组练习内容生成
     * @param groupSize 每组字符数量（2-5）
     * @param groupCount 组数
     * @return 空格分隔的字符组
     */
    fun generateCharacterGroups(groupSize: Int = 3, groupCount: Int = 10): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val size = groupSize.coerceIn(2, 5)
        return (1..groupCount).map {
            (1..size).map { chars.random() }.joinToString("")
        }.joinToString(" ")
    }

    /**
     * 呼号训练内容生成
     * @param count 呼号数量
     * @return 空格分隔的随机呼号
     */
    fun generateCallsigns(count: Int = 10): String {
        val prefixes = listOf("BY", "BA", "BV", "BG", "BD", "BI", "BH", "BT", "BS")
        val suffixes = listOf(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
        )
        return (1..count).map {
            val prefix = prefixes.random()
            val digit = (1..9).random()
            val suffix = (1..3).map { suffixes.random() }.joinToString("")
            "$prefix$digit$suffix"
        }.joinToString(" ")
    }

    /**
     * 文本训练内容生成
     * @param sentenceCount 句子数量
     * @return CW常用文本组合
     */
    fun generateCWText(sentenceCount: Int = 5): String {
        val templates = listOf(
            { "CQ CQ CQ DE ${generateCallsigns(1)} ${generateCallsigns(1)} K" },
            { "DE ${generateCallsigns(1)} ${generateCallsigns(1)} = UR RST 599 599 = NAME IS ${cwCommonWords.random()} ES QTH IS ${cwCommonWords.random()} = BK" },
            { "${generateCallsigns(1)} DE ${generateCallsigns(1)} = TNX FER RPT UR RST 579 579 = WX HR IS ${cwCommonWords.random()} = BK" },
            { "73 ES TNX FER QSO ${generateCallsigns(1)} DE ${generateCallsigns(1)} SK" },
            { "TEST ${generateCallsigns(1)} ${generateCallsigns(1)} = NR ? BK" }
        )
        return (1..sentenceCount).map { templates.random()() }.joinToString(" ")
    }

    /**
     * 获取教程练习内容
     * @param courseId 课程类型（1=Koch, 2=字符组, 3=呼号, 4=文本）
     * @param lessonId 课程ID（Koch课程为1-26）
     * @param length 文本长度
     * @return 练习文本
     */
    fun getTutorialContent(courseId: Int, lessonId: Int, length: Int = 25): String {
        return when (courseId) {
            1 -> generateKochLesson(lessonId, length)
            2 -> generateCharacterGroups(3, length / 4)
            3 -> generateCallsigns(maxOf(1, length / 6))
            4 -> generateCWText(maxOf(1, length / 50))
            else -> generateKochLesson(1, length)
        }
    }
}