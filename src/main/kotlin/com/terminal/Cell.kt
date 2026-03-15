package com.terminal

/**
 * A single character cell in the terminal grid.
 * Immutable — writing to a cell replaces the reference, never mutates.
 */
data class Cell(
    val char: Char = ' ',
    val fg: Color = Color.DEFAULT,
    val bg: Color = Color.DEFAULT,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
) {
    companion object {
        /** The canonical blank cell — space with all attributes at their defaults. */
        val BLANK = Cell()

        /** Create a cell from a character and a [TextAttributes] snapshot. */
        fun of(char: Char, attrs: TextAttributes) = Cell(
            char = char,
            fg = attrs.fg,
            bg = attrs.bg,
            bold = attrs.bold,
            italic = attrs.italic,
            underline = attrs.underline,
        )
    }
}
