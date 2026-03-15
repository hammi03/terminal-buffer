package com.terminal

/**
 * A fixed-size terminal text buffer with bounded scrollback.
 *
 * Screen invariants (always hold):
 *  - exactly [height] lines, each exactly [width] cells
 *  - cursor is always within [0, height) × [0, width)
 *  - scrollback holds at most [maxScrollback] lines (oldest dropped first)
 *
 * @param width        number of columns
 * @param height       number of visible rows
 * @param maxScrollback maximum lines retained in scrollback history (0 = disabled)
 */
class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollback: Int = 1000,
) {
    init {
        require(width > 0)        { "width must be > 0" }
        require(height > 0)       { "height must be > 0" }
        require(maxScrollback >= 0) { "maxScrollback must be >= 0" }
    }

    // Visible screen — screen[0] is the top row.
    private val screen: Array<Line> = Array(height) { Line(width) }

    // Scrollback — index 0 is the oldest (topmost) scrolled-off line.
    private val scrollback: ArrayDeque<Line> = ArrayDeque()

    private var cursorRow: Int = 0
    private var cursorCol: Int = 0

    /** The attributes applied to every cell written from this point forward. */
    var currentAttributes: TextAttributes = TextAttributes()
        private set

    // ── Attributes ────────────────────────────────────────────────────────────

    fun setAttributes(attrs: TextAttributes) { currentAttributes = attrs }

    // ── Cursor ────────────────────────────────────────────────────────────────

    fun getCursorRow(): Int = cursorRow
    fun getCursorCol(): Int = cursorCol

    /** Move cursor to (row, col), clamped to valid bounds. */
    fun setCursor(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, height - 1)
        cursorCol = col.coerceIn(0, width - 1)
    }

    /** Move cursor by (dRow, dCol), clamped to valid bounds. */
    fun moveCursor(dRow: Int, dCol: Int) = setCursor(cursorRow + dRow, cursorCol + dCol)

    // ── Write — overwrite mode ────────────────────────────────────────────────

    /**
     * Write [text] at the cursor in overwrite mode.
     * Each character replaces the cell at the current cursor position.
     * The cursor advances after each character; at the end of a line it wraps
     * to column 0 of the next line. At the bottom of the screen the buffer
     * scrolls up one line.
     */
    fun writeText(text: String) {
        for (ch in text) {
            screen[cursorRow][cursorCol] = Cell.of(ch, currentAttributes)
            advanceCursor()
        }
    }

    // ── Insert — insert mode ──────────────────────────────────────────────────

    /**
     * Insert [text] at the cursor in insert mode.
     * Each character is inserted at the current cursor position; existing
     * content on the current line shifts right by one. The cell pushed off the
     * right edge cascades to column 0 of the next line, propagating all the way
     * to the bottom of the visible screen.
     *
     * **Overflow policy:** content that would be pushed past the last row of the
     * visible screen is silently discarded. Insert mode does not trigger a scroll;
     * only cursor advancement past the last row (as in [writeText]) does.
     * This matches the behaviour of classic terminal insert mode (e.g. VT100 ICH),
     * where inserted characters shift within the current page and excess is lost.
     *
     * The cursor advances exactly as in [writeText].
     */
    fun insertText(text: String) {
        for (ch in text) {
            var overflow: Cell = screen[cursorRow].insertAt(cursorCol, Cell.of(ch, currentAttributes))
            for (row in cursorRow + 1 until height) {
                overflow = screen[row].insertAt(0, overflow)
            }
            // overflow past the last line is discarded
            advanceCursor()
        }
    }

    // ── Line operations ───────────────────────────────────────────────────────

    /**
     * Fill every cell in [row] with [char] using [currentAttributes].
     * [row] is clamped to valid bounds.
     */
    fun fillLine(row: Int, char: Char = ' ') {
        screen[row.coerceIn(0, height - 1)].fill(Cell.of(char, currentAttributes))
    }

    /**
     * Push the top screen line into scrollback, scroll all lines up by one,
     * and place a blank line at the bottom. Cursor position is unchanged.
     */
    fun insertLineAtBottom() = scrollUp()

    // ── Screen operations ─────────────────────────────────────────────────────

    /**
     * Clear the visible screen (all cells become blank) and reset the cursor
     * to (0, 0). Scrollback is preserved.
     */
    fun clearScreen() {
        for (i in screen.indices) screen[i] = Line(width)
        cursorRow = 0
        cursorCol = 0
    }

    /**
     * Clear the visible screen, reset the cursor to (0, 0), and discard all
     * scrollback history.
     */
    fun clearAll() {
        clearScreen()
        scrollback.clear()
    }

    // ── Read — visible screen ─────────────────────────────────────────────────

    /** Return the [Cell] at screen position ([row], [col]). */
    fun readCell(row: Int, col: Int): Cell = screen[row][col]

    /** Return the content of screen row [row] as a plain string. */
    fun readLine(row: Int): String = screen[row].asString()

    /** Return the full visible screen as a newline-separated string. */
    fun readScreen(): String = screen.joinToString("\n") { it.asString() }

    // ── Read — scrollback ─────────────────────────────────────────────────────

    /** Number of lines currently stored in scrollback. */
    fun scrollbackSize(): Int = scrollback.size

    /**
     * Return the [Cell] at scrollback row [row] and [col].
     * Row 0 is the oldest (topmost) scrolled-off line.
     */
    fun readScrollbackCell(row: Int, col: Int): Cell = scrollback[row][col]

    /** Return scrollback row [row] as a plain string. Row 0 is the oldest line. */
    fun readScrollbackLine(row: Int): String = scrollback[row].asString()

    // ── Read — global coordinate space (scrollback + screen) ─────────────────

    /**
     * Total logical lines: scrollback rows followed by visible screen rows.
     * Global row 0 is the oldest scrollback line; global row [totalLines]-1
     * is the bottom of the visible screen.
     */
    val totalLines: Int get() = scrollback.size + height

    /**
     * Return the [Cell] at the given global row and [col].
     * Global rows 0..[scrollbackSize)-1 address scrollback;
     * rows [scrollbackSize]..[totalLines)-1 address the visible screen.
     */
    fun readGlobalCell(globalRow: Int, col: Int): Cell {
        val sbSize = scrollback.size
        return if (globalRow < sbSize) scrollback[globalRow][col]
               else screen[globalRow - sbSize][col]
    }

    /**
     * Return the global row at [globalRow] as a plain string.
     * Uses the same indexing as [readGlobalCell].
     */
    fun readGlobalLine(globalRow: Int): String {
        val sbSize = scrollback.size
        return if (globalRow < sbSize) scrollback[globalRow].asString()
               else screen[globalRow - sbSize].asString()
    }

    // ── Read — full history ───────────────────────────────────────────────────

    /**
     * Return scrollback followed by the visible screen as a single
     * newline-separated string. The oldest scrollback line is first.
     */
    fun readAll(): String = buildString {
        scrollback.forEach { append(it.asString()).append('\n') }
        append(readScreen())
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Advance the cursor by one cell. Wraps to the next line at the right edge;
     * scrolls the screen up when the cursor moves past the last row.
     */
    private fun advanceCursor() {
        cursorCol++
        if (cursorCol >= width) {
            cursorCol = 0
            cursorRow++
            if (cursorRow >= height) {
                scrollUp()
                cursorRow = height - 1
            }
        }
    }

    /**
     * Transfer the top screen line to scrollback (dropping the oldest entry if
     * the scrollback is full), shift all remaining lines up by one, and place a
     * fresh blank line at the bottom.
     *
     * The transferred [Line] object is never touched again by the screen, so it
     * becomes immutable history from this point forward.
     */
    private fun scrollUp() {
        if (maxScrollback > 0) {
            if (scrollback.size >= maxScrollback) scrollback.removeFirst()
            scrollback.addLast(screen[0])   // ownership transfers to scrollback
        }
        for (i in 0 until height - 1) screen[i] = screen[i + 1]
        screen[height - 1] = Line(width)
    }
}
