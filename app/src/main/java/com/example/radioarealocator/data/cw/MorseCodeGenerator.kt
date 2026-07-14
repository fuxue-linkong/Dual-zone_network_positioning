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
        '/' to "-..-.", '(' to "-.--.-", ')' to "-.--.-"
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
}