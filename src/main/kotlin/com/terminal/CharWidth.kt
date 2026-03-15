package com.terminal

/**
 * Determines the display width (in terminal columns) of a BMP character.
 *
 * Returns 2 for East Asian wide characters — CJK ideographs, Hangul syllables,
 * fullwidth forms, and related blocks.  Returns 1 for everything else.
 *
 * Limitation: supplementary codepoints above U+FFFF (many emoji, rare CJK
 * extensions) require surrogate pairs on the JVM and are not handled here.
 * They are treated as two separate characters of width 1 each.  Supporting
 * them would require a codepoint-aware API; see README for details.
 */
object CharWidth {

    fun of(ch: Char): Int = if (isWide(ch)) 2 else 1

    private fun isWide(ch: Char): Boolean {
        val cp = ch.code
        return cp in 0x1100..0x115F    // Hangul Jamo
            || cp in 0x2E80..0x303E    // CJK Radicals Supplement … CJK Symbols
            || cp in 0x3040..0x33FF    // Hiragana, Katakana, Bopomofo, CJK Compat.
            || cp in 0x3400..0x4DBF    // CJK Unified Ideographs Extension A
            || cp in 0x4E00..0x9FFF    // CJK Unified Ideographs
            || cp in 0xA000..0xA4CF    // Yi
            || cp in 0xAC00..0xD7AF    // Hangul Syllables
            || cp in 0xF900..0xFAFF    // CJK Compatibility Ideographs
            || cp in 0xFE10..0xFE1F    // Vertical Forms
            || cp in 0xFE30..0xFE6F    // CJK Compatibility Forms / Small Form Variants
            || cp in 0xFF01..0xFF60    // Fullwidth Latin, Katakana, Bopomofo
            || cp in 0xFFE0..0xFFE6    // Fullwidth Signs
    }
}
