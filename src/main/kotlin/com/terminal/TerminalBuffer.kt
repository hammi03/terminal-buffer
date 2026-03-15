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
    width: Int,
    height: Int,
    val maxScrollback: Int = 1000,
) {
    init {
        require(width > 0)          { "width must be > 0" }
        require(height > 0)         { "height must be > 0" }
        require(maxScrollback >= 0) { "maxScrollback must be >= 0" }
    }

    // Backing fields — mutable to support resize().
    private var _width:  Int = width
    private var _height: Int = height

    val width:  Int get() = _width
    val height: Int get() = _height

    // Visible screen — screen[0] is the top row.
    private var screen: Array<Line> = Array(height) { Line(width) }

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
        cursorRow = row.coerceIn(0, _height - 1)
        cursorCol = col.coerceIn(0, _width - 1)
    }

    /** Move cursor by (dRow, dCol), clamped to valid bounds. */
    fun moveCursor(dRow: Int, dCol: Int) = setCursor(cursorRow + dRow, cursorCol + dCol)

    // ── Write — overwrite mode ────────────────────────────────────────────────

    /**
     * Write [text] at the cursor in overwrite mode.
     * Each character replaces the cell(s) at the current cursor position.
     * Wide characters occupy two columns; if a wide character does not fit
     * in the last column of a line the final column is filled with a blank
     * and the wide character is written at the start of the next line.
     * The cursor advances after each character (by 2 for wide characters).
     * At the bottom of the screen the buffer scrolls up one line.
     */
    fun writeText(text: String) {
        for (ch in text) {
            val w = CharWidth.of(ch)
            if (w == 2 && cursorCol == _width - 1) {
                // Wide char won't fit — blank last column and wrap.
                clearWideRemnants(cursorRow, cursorCol)
                screen[cursorRow][cursorCol] = Cell.BLANK
                advanceCursor()
            }
            clearWideRemnants(cursorRow, cursorCol)
            screen[cursorRow][cursorCol] = Cell.of(ch, currentAttributes)
            if (w == 2) {
                clearWideRemnants(cursorRow, cursorCol + 1)
                screen[cursorRow][cursorCol + 1] = Cell.CONTINUATION
            }
            advanceCursor(w)
        }
    }

    // ── Insert — insert mode ──────────────────────────────────────────────────

    /**
     * Insert [text] at the cursor in insert mode.
     * Each character is inserted at the current cursor position; existing
     * content on the current line shifts right.  Wide characters consume two
     * columns and are inserted as a LEAD + CONTINUATION pair.
     * Overflow cascades to subsequent lines; content pushed past the last row
     * is discarded (see README for rationale).
     * The cursor advances exactly as in [writeText].
     */
    fun insertText(text: String) {
        for (ch in text) {
            val w = CharWidth.of(ch)
            if (w == 2 && cursorCol >= _width - 1) {
                // Wide char won't fit — insert blank at current pos and wrap.
                var ov: Cell = screen[cursorRow].insertAt(cursorCol, Cell.BLANK)
                for (row in cursorRow + 1 until _height) ov = screen[row].insertAt(0, ov)
                advanceCursor()
            }
            var overflow: Cell = screen[cursorRow].insertAt(cursorCol, Cell.of(ch, currentAttributes))
            for (row in cursorRow + 1 until _height) overflow = screen[row].insertAt(0, overflow)
            if (w == 2) {
                var ov2: Cell = screen[cursorRow].insertAt(cursorCol + 1, Cell.CONTINUATION)
                for (row in cursorRow + 1 until _height) ov2 = screen[row].insertAt(0, ov2)
            }
            advanceCursor(w)
        }
    }

    // ── Line operations ───────────────────────────────────────────────────────

    /**
     * Fill every cell in [row] with [char] using [currentAttributes].
     * [row] is clamped to valid bounds.
     */
    fun fillLine(row: Int, char: Char = ' ') {
        screen[row.coerceIn(0, _height - 1)].fill(Cell.of(char, currentAttributes))
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
        screen = Array(_height) { Line(_width) }
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

    // ── Resize ────────────────────────────────────────────────────────────────

    /**
     * Change the buffer dimensions to [newWidth] × [newHeight].
     *
     * **Width change**
     * Each line (screen and scrollback) is resized:
     * - Growing: blank cells are appended on the right.
     * - Shrinking: cells beyond the new width are discarded.
     * Wide characters that straddle the new right edge may become orphaned;
     * this is noted as a known limitation (see README).
     *
     * **Height change**
     * - Shrinking: the top `oldHeight − newHeight` screen rows are pushed into
     *   scrollback (subject to [maxScrollback]) before the screen is trimmed.
     *   This preserves content as history, mirroring normal scroll behaviour.
     * - Growing: blank rows are appended at the bottom of the visible screen.
     *
     * The cursor is clamped to the new bounds after resize.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        require(newWidth  > 0) { "newWidth must be > 0" }
        require(newHeight > 0) { "newHeight must be > 0" }

        val oldWidth  = _width
        val oldHeight = _height

        // ── Width ─────────────────────────────────────────────────────────────
        if (newWidth != oldWidth) {
            fun resizeLine(old: Line): Line {
                val newLine = Line(newWidth)
                val copy = minOf(oldWidth, newWidth)
                for (col in 0 until copy) newLine[col] = old[col]
                return newLine
            }
            for (i in screen.indices) screen[i] = resizeLine(screen[i])
            val resized = scrollback.map { resizeLine(it) }
            scrollback.clear()
            resized.forEach { scrollback.addLast(it) }
            _width = newWidth
        }

        // ── Height ────────────────────────────────────────────────────────────
        if (newHeight < oldHeight) {
            // Shrink: push top rows into scrollback, keep the bottom rows.
            val rowsToPush = oldHeight - newHeight
            for (i in 0 until rowsToPush) {
                if (maxScrollback > 0) {
                    if (scrollback.size >= maxScrollback) scrollback.removeFirst()
                    scrollback.addLast(screen[i])
                }
            }
            screen = Array(newHeight) { i -> screen[i + rowsToPush] }
        } else if (newHeight > oldHeight) {
            // Grow: append blank rows at the bottom.
            screen = Array(newHeight) { i ->
                if (i < oldHeight) screen[i] else Line(_width)
            }
        }
        _height = newHeight

        // Cursor must stay in bounds after resize.
        cursorRow = cursorRow.coerceIn(0, _height - 1)
        cursorCol = cursorCol.coerceIn(0, _width - 1)
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
    val totalLines: Int get() = scrollback.size + _height

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
     * Clear any wide-character remnants at (row, col) before writing there.
     * - If (row, col) holds a CONTINUATION, blank the LEAD cell to its left.
     * - If (row, col) holds a LEAD, blank the CONTINUATION cell to its right.
     */
    private fun clearWideRemnants(row: Int, col: Int) {
        val cell = screen[row][col]
        if (cell.isContinuation && col > 0) {
            screen[row][col - 1] = Cell.BLANK
        } else if (!cell.isContinuation && col + 1 < _width && screen[row][col + 1].isContinuation) {
            screen[row][col + 1] = Cell.BLANK
        }
    }

    /**
     * Advance the cursor by [steps] columns. Wraps to the next line at the
     * right edge; scrolls the screen up when the cursor moves past the last row.
     */
    private fun advanceCursor(steps: Int = 1) {
        cursorCol += steps
        while (cursorCol >= _width) {
            cursorCol -= _width
            cursorRow++
            if (cursorRow >= _height) {
                scrollUp()
                cursorRow = _height - 1
            }
        }
    }

    /**
     * Transfer the top screen line to scrollback (dropping the oldest entry if
     * the scrollback is full), shift all remaining lines up by one, and place a
     * fresh blank line at the bottom.
     */
    private fun scrollUp() {
        if (maxScrollback > 0) {
            if (scrollback.size >= maxScrollback) scrollback.removeFirst()
            scrollback.addLast(screen[0])
        }
        for (i in 0 until _height - 1) screen[i] = screen[i + 1]
        screen[_height - 1] = Line(_width)
    }
}
