package com.terminal

/**
 * A fixed-width row of [Cell]s.
 *
 * Lines in the visible screen are mutable. Once a line is pushed into
 * the scrollback it is never referenced by the screen again, making it
 * effectively immutable history.
 */
class Line(val width: Int) {

    private val cells: Array<Cell> = Array(width) { Cell.BLANK }

    operator fun get(col: Int): Cell = cells[col]
    operator fun set(col: Int, cell: Cell) { cells[col] = cell }

    /** Overwrite every cell with [cell]. */
    fun fill(cell: Cell = Cell.BLANK) { cells.fill(cell) }

    /**
     * Insert [cell] at [col], shifting everything to the right by one position.
     * Returns the cell that was pushed off the right edge.
     */
    fun insertAt(col: Int, cell: Cell): Cell {
        val overflow = cells[width - 1]
        for (i in width - 1 downTo col + 1) cells[i] = cells[i - 1]
        cells[col] = cell
        return overflow
    }

    /** Returns the line content as a plain string (chars only). */
    fun asString(): String = String(CharArray(width) { cells[it].char })
}
