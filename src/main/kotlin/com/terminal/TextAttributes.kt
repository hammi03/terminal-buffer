package com.terminal

/**
 * The active text attributes used when writing to the buffer.
 * Captured as a snapshot into each [Cell] at write time.
 */
data class TextAttributes(
    val fg: Color = Color.DEFAULT,
    val bg: Color = Color.DEFAULT,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)
