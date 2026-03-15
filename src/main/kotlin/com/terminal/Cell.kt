package com.terminal

/**
 * A single character cell in the terminal grid.
 * Immutable — writing to a cell replaces the reference, never mutates.
 *
 * Wide characters (e.g. CJK ideographs) occupy two consecutive cells.
 * The left cell carries the character; the right cell is a [CONTINUATION]
 * marker ([isContinuation] = true, [char] = ' ').  Both cells share the
 * same foreground/background/style attributes.
 */
data class Cell(
    val char: Char = ' ',
    val fg: Color = Color.DEFAULT,
    val bg: Color = Color.DEFAULT,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** True for the right-hand half of a wide character. */
    val isContinuation: Boolean = false,
) {
    companion object {
        /** The canonical blank cell. */
        val BLANK = Cell()

        /** Placeholder occupying the right column of a wide character. */
        val CONTINUATION = Cell(isContinuation = true)

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
