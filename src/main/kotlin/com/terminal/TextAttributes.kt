package com.terminal

/**
 * The active text attributes used when writing to the buffer.
 * Captured as a snapshot into each [Cell] at write time.
 */
data class TextAttributes(
    val fg: Int = 0,
    val bg: Int = 0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)
