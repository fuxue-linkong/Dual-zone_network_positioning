package com.example.radioarealocator.data.cw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseCodeGeneratorTest {
    private val generator = MorseCodeGenerator()

    // ---- 基础摩尔斯电码测试 ----

    @Test
    fun `test generate morse code for letter A`() {
        val result = generator.toMorseCode("A")
        assertEquals(".-", result)
    }

    @Test
    fun `test generate morse code for word HI`() {
        val result = generator.toMorseCode("HI")
        assertEquals(".... ..", result)
    }

    @Test
    fun `test generate random characters`() {
        val result = generator.generateRandomCharacters(CharacterSet.LETTERS, 10)
        assertEquals(10, result.length)
    }

    @Test
    fun `test toMorseCode handles empty string`() {
        val result = generator.toMorseCode("")
        assertEquals("", result)
    }

    @Test
    fun `test toMorseCode handles numbers`() {
        val result = generator.toMorseCode("0")
        assertEquals("-----", result)
    }

    @Test
    fun `test toMorseCode handles lowercase conversion`() {
        val result = generator.toMorseCode("a")
        assertEquals(".-", result)
    }

    // ---- Koch课程测试 ----

    @Test
    fun `test generateKochLesson returns correct length`() {
        val result = generator.generateKochLesson(1, 25)
        assertEquals(25, result.length)
    }

    @Test
    fun `test generateKochLesson lesson 1 contains only K and M`() {
        // Koch课程第1课只包含K和M
        val result = generator.generateKochLesson(1, 100)
        assertTrue(result.all { it == 'K' || it == 'M' })
    }

    @Test
    fun `test generateKochLesson lesson 2 contains K M U`() {
        // Koch课程第2课包含K, M, U
        val result = generator.generateKochLesson(2, 100)
        assertTrue(result.all { it in "KMU" })
    }

    @Test
    fun `test generateKochLesson lesson 3 contains K M U R E`() {
        // Koch课程第3课包含K, M, U, R, E
        val result = generator.generateKochLesson(3, 100)
        assertTrue(result.all { it in "KMURE" })
    }

    @Test
    fun `test generateKochLesson max lesson is 26`() {
        // 超过26课应该限制为26
        val result = generator.generateKochLesson(30, 10)
        assertNotNull(result)
        assertEquals(10, result.length)
    }

    @Test
    fun `test generateKochLesson min lesson is 1`() {
        // 小于1课应该限制为1
        val result = generator.generateKochLesson(0, 10)
        assertNotNull(result)
        assertEquals(10, result.length)
    }

    @Test
    fun `test getKochLessonChars returns correct chars`() {
        // Koch字符顺序: KMURESNAPTLWI.JZ=FOY,VG5/Q92H38B?47C1D60X
        val chars1 = generator.getKochLessonChars(1)
        assertEquals(1, chars1.length)
        assertEquals('K', chars1[0])

        val chars3 = generator.getKochLessonChars(3)
        assertEquals(3, chars3.length)
        assertEquals("KMU", chars3)

        val chars5 = generator.getKochLessonChars(5)
        assertEquals(5, chars5.length)
        assertEquals("KMURE", chars5)
    }

    @Test
    fun `test getKochLessonChars returns all 26 chars for lesson 26`() {
        val chars = generator.getKochLessonChars(26)
        assertEquals(26, chars.length)
    }

    // ---- 字符组练习测试 ----

    @Test
    fun `test generateCharacterGroups returns correct format`() {
        val result = generator.generateCharacterGroups(3, 5)
        val groups = result.split(" ")
        assertEquals(5, groups.size)
        assertTrue(groups.all { it.length == 3 })
    }

    @Test
    fun `test generateCharacterGroups group size 2`() {
        val result = generator.generateCharacterGroups(2, 10)
        val groups = result.split(" ")
        assertEquals(10, groups.size)
        assertTrue(groups.all { it.length == 2 })
    }

    @Test
    fun `test generateCharacterGroups group size 5`() {
        val result = generator.generateCharacterGroups(5, 3)
        val groups = result.split(" ")
        assertEquals(3, groups.size)
        assertTrue(groups.all { it.length == 5 })
    }

    @Test
    fun `test generateCharacterGroups respects min group size`() {
        val result = generator.generateCharacterGroups(1, 5)
        val groups = result.split(" ")
        // 最小分组大小为2
        assertTrue(groups.all { it.length >= 2 })
    }

    @Test
    fun `test generateCharacterGroups respects max group size`() {
        val result = generator.generateCharacterGroups(10, 5)
        val groups = result.split(" ")
        // 最大分组大小为5
        assertTrue(groups.all { it.length <= 5 })
    }

    // ---- 呼号训练测试 ----

    @Test
    fun `test generateCallsigns returns correct count`() {
        val result = generator.generateCallsigns(10)
        val callsigns = result.split(" ")
        assertEquals(10, callsigns.size)
    }

    @Test
    fun `test generateCallsigns format is valid`() {
        val result = generator.generateCallsigns(50)
        val callsigns = result.split(" ")
        val validPrefixes = listOf("BY", "BA", "BV", "BG", "BD", "BI", "BH", "BT", "BS")

        callsigns.forEach { callsign ->
            // 呼号格式: 前缀(2字母) + 数字(1-9) + 后缀(1-3字母)
            assertTrue("呼号长度应>=4: $callsign", callsign.length >= 4)
            assertTrue("呼号前缀应为有效值: $callsign", validPrefixes.any { callsign.startsWith(it) })
            assertTrue("呼号第三位应为数字: $callsign", callsign[2].isDigit())
            assertTrue("呼号数字应为1-9: $callsign", callsign[2] in '1'..'9')
        }
    }

    @Test
    fun `test generateCallsigns single call`() {
        val result = generator.generateCallsigns(1)
        assertFalse(result.contains(" "))
    }

    // ---- 文本训练测试 ----

    @Test
    fun `test generateCWText returns non-empty result`() {
        val result = generator.generateCWText(3)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `test generateCWText contains CW keywords`() {
        val result = generator.generateCWText(10)
        // 文本应该包含CW常用关键词
        assertTrue("应包含CQ或DE或73", result.contains("CQ") || result.contains("DE") || result.contains("73") || result.contains("TEST"))
    }

    // ---- getTutorialContent测试 ----

    @Test
    fun `test getTutorialContent courseId 1 returns Koch lesson`() {
        val result = generator.getTutorialContent(1, 1, 20)
        assertEquals(20, result.length)
        assertTrue(result.all { it in "KM" })
    }

    @Test
    fun `test getTutorialContent courseId 2 returns character groups`() {
        val result = generator.getTutorialContent(2, 1, 20)
        assertTrue(result.isNotEmpty())
        assertTrue(result.contains(" "))
    }

    @Test
    fun `test getTutorialContent courseId 3 returns callsigns`() {
        val result = generator.getTutorialContent(3, 1, 20)
        assertTrue(result.isNotEmpty())
        assertTrue(result.contains(" "))
    }

    @Test
    fun `test getTutorialContent courseId 4 returns CW text`() {
        val result = generator.getTutorialContent(4, 1, 50)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `test getTutorialContent default returns Koch lesson`() {
        val result = generator.getTutorialContent(99, 1, 20)
        assertEquals(20, result.length)
    }

    // ---- getCharacterSets测试 ----

    @Test
    fun `test getCharacterSets returns all sets`() {
        val sets = generator.getCharacterSets()
        assertEquals(4, sets.size)
        assertTrue(sets.contains(CharacterSet.LETTERS))
        assertTrue(sets.contains(CharacterSet.NUMBERS))
        assertTrue(sets.contains(CharacterSet.SYMBOLS))
        assertTrue(sets.contains(CharacterSet.CUSTOM))
    }
}
