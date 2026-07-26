package com.example.radioarealocator.data.cw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MorseCodeGenerator] 单元测试。
 *
 * 覆盖：
 * - 字符到摩尔斯电码的转换
 * - 随机字符生成
 * - Koch 课程内容与字符集
 * - 呼号/文本/字符组生成
 * - getTutorialContent 按课程类型分发
 */
class MorseCodeGeneratorExtTest {

    private val generator = MorseCodeGenerator()

    // ---- toMorseCode ----

    @Test
    fun `toMorseCode converts simple letters`() {
        val result = generator.toMorseCode("SOS")
        assertEquals("... --- ...", result)
    }

    @Test
    fun `toMorseCode converts lowercase to uppercase`() {
        val result = generator.toMorseCode("sos")
        assertEquals("... --- ...", result)
    }

    @Test
    fun `toMorseCode converts numbers`() {
        val result = generator.toMorseCode("123")
        assertEquals(".---- ..--- ...--", result)
    }

    @Test
    fun `toMorseCode converts single letter A`() {
        assertEquals(".-", generator.toMorseCode("A"))
    }

    @Test
    fun `toMorseCode converts space to slash`() {
        assertEquals(".- / -...", generator.toMorseCode("A B"))
    }

    @Test
    fun `toMorseCode skips unknown characters`() {
        // @ is not in the map, should be skipped
        val result = generator.toMorseCode("A@B")
        assertEquals(".- -...", result)
    }

    @Test
    fun `toMorseCode returns empty for empty string`() {
        assertEquals("", generator.toMorseCode(""))
    }

    @Test
    fun `toMorseCode handles chinese characters by skipping them`() {
        // Chinese chars are not in the map, should produce empty or skip
        val result = generator.toMorseCode("你好")
        assertEquals("", result)
    }

    // ---- generateRandomCharacters ----

    @Test
    fun `generateRandomCharacters with LETTERS produces only letters`() {
        val result = generator.generateRandomCharacters(CharacterSet.LETTERS, 50)
        assertEquals(50, result.length)
        assertTrue(result.all { it.isLetter() })
    }

    @Test
    fun `generateRandomCharacters with NUMBERS produces only digits`() {
        val result = generator.generateRandomCharacters(CharacterSet.NUMBERS, 30)
        assertEquals(30, result.length)
        assertTrue(result.all { it.isDigit() })
    }

    @Test
    fun `generateRandomCharacters with SYMBOLS produces only symbols`() {
        val result = generator.generateRandomCharacters(CharacterSet.SYMBOLS, 20)
        assertEquals(20, result.length)
        assertTrue(result.all { it in ".,?!-/()" })
    }

    @Test
    fun `generateRandomCharacters with CUSTOM produces letters or digits`() {
        val result = generator.generateRandomCharacters(CharacterSet.CUSTOM, 50)
        assertEquals(50, result.length)
        assertTrue(result.all { it.isLetterOrDigit() })
    }

    @Test
    fun `generateRandomCharacters with length 0 returns empty`() {
        val result = generator.generateRandomCharacters(CharacterSet.LETTERS, 0)
        assertEquals("", result)
    }

    // ---- getCharacterSets ----

    @Test
    fun `getCharacterSets returns all enum values`() {
        val sets = generator.getCharacterSets()
        assertEquals(4, sets.size)
        assertTrue(sets.contains(CharacterSet.LETTERS))
        assertTrue(sets.contains(CharacterSet.NUMBERS))
        assertTrue(sets.contains(CharacterSet.SYMBOLS))
        assertTrue(sets.contains(CharacterSet.CUSTOM))
    }

    // ---- generateKochLesson ----

    @Test
    fun `generateKochLesson with lesson 1 produces single char`() {
        val result = generator.generateKochLesson(1, 10)
        assertEquals(10, result.length)
        // Lesson 1 only uses "K"
        assertTrue(result.all { it == 'K' })
    }

    @Test
    fun `generateKochLesson with lesson 2 produces K or M`() {
        val result = generator.generateKochLesson(2, 50)
        assertEquals(50, result.length)
        assertTrue(result.all { it == 'K' || it == 'M' })
    }

    @Test
    fun `generateKochLesson respects length parameter`() {
        assertEquals(5, generator.generateKochLesson(1, 5).length)
        assertEquals(100, generator.generateKochLesson(1, 100).length)
    }

    @Test
    fun `generateKochLesson clamps lesson to valid range`() {
        // Lesson 0 should be treated as 1
        val result0 = generator.generateKochLesson(0, 10)
        assertEquals(10, result0.length)
        // Lesson > 26 should be treated as 26
        val resultMax = generator.generateKochLesson(100, 10)
        assertEquals(10, resultMax.length)
    }

    // ---- getKochLessonChars ----

    @Test
    fun `getKochLessonChars returns correct character count`() {
        assertEquals(1, generator.getKochLessonChars(1).length)
        assertEquals(5, generator.getKochLessonChars(5).length)
        assertEquals(26, generator.getKochLessonChars(26).length)
    }

    @Test
    fun `getKochLessonChars clamps to valid range`() {
        assertEquals(1, generator.getKochLessonChars(0).length)
        assertEquals(26, generator.getKochLessonChars(100).length)
    }

    // ---- generateCharacterGroups ----

    @Test
    fun `generateCharacterGroups produces space-separated groups`() {
        val result = generator.generateCharacterGroups(3, 5)
        val groups = result.split(" ")
        assertEquals(5, groups.size)
        assertTrue(groups.all { it.length == 3 })
    }

    @Test
    fun `generateCharacterGroups clamps group size`() {
        val result = generator.generateCharacterGroups(10, 3)
        val groups = result.split(" ")
        assertTrue(groups.all { it.length <= 5 })
    }

    // ---- generateCallsigns ----

    @Test
    fun `generateCallsigns produces space-separated callsigns`() {
        val result = generator.generateCallsigns(5)
        val calls = result.split(" ")
        assertEquals(5, calls.size)
    }

    @Test
    fun `generateCallsigns each callsign starts with valid prefix`() {
        val validPrefixes = listOf("BY", "BA", "BV", "BG", "BD", "BI", "BH", "BT", "BS")
        val result = generator.generateCallsigns(10)
        val calls = result.split(" ")
        assertTrue(calls.all { call -> validPrefixes.any { call.startsWith(it) } })
    }

    // ---- generateCWText ----

    @Test
    fun `generateCWText produces non-empty text`() {
        val result = generator.generateCWText(3)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `generateCWText with 0 sentences returns empty-ish`() {
        val result = generator.generateCWText(0)
        // Should be empty or just whitespace
        assertTrue(result.isBlank() || result.isEmpty())
    }

    // ---- getTutorialContent ----

    @Test
    fun `getTutorialContent for course 1 uses Koch lesson`() {
        val result = generator.getTutorialContent(1, 1, 25)
        assertEquals(25, result.length)
    }

    @Test
    fun `getTutorialContent for course 2 uses character groups`() {
        val result = generator.getTutorialContent(2, 1, 20)
        assertTrue(result.isNotBlank())
        val groups = result.split(" ")
        assertTrue(groups.isNotEmpty())
    }

    @Test
    fun `getTutorialContent for course 3 uses callsigns`() {
        val result = generator.getTutorialContent(3, 1, 30)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `getTutorialContent for course 4 uses CW text`() {
        val result = generator.getTutorialContent(4, 1, 100)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `getTutorialContent for unknown course falls back to Koch`() {
        val result = generator.getTutorialContent(99, 1, 25)
        assertEquals(25, result.length)
    }
}
