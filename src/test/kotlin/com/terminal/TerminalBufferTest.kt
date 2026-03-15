package com.terminal

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TerminalBufferTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buf(w: Int = 10, h: Int = 5, sb: Int = 100) = TerminalBuffer(w, h, sb)

    // ── Construction ─────────────────────────────────────────────────────────

    @Nested inner class Construction {

        @Test fun `initial cursor is at top-left`() {
            val b = buf()
            assertEquals(0, b.getCursorRow())
            assertEquals(0, b.getCursorCol())
        }

        @Test fun `initial screen is all blanks`() {
            val b = buf(4, 3)
            assertEquals("    \n    \n    ", b.readScreen())
        }

        @Test fun `initial scrollback is empty`() {
            assertEquals(0, buf().scrollbackSize())
        }

        @Test fun `invalid dimensions throw`() {
            assertThrows(IllegalArgumentException::class.java) { TerminalBuffer(0, 5) }
            assertThrows(IllegalArgumentException::class.java) { TerminalBuffer(5, 0) }
            assertThrows(IllegalArgumentException::class.java) { TerminalBuffer(5, 5, -1) }
        }
    }

    // ── Cursor ────────────────────────────────────────────────────────────────

    @Nested inner class Cursor {

        @Test fun `setCursor moves cursor`() {
            val b = buf(10, 5)
            b.setCursor(3, 7)
            assertEquals(3, b.getCursorRow())
            assertEquals(7, b.getCursorCol())
        }

        @Test fun `setCursor clamps row below zero`() {
            val b = buf()
            b.setCursor(-5, 0)
            assertEquals(0, b.getCursorRow())
        }

        @Test fun `setCursor clamps row above height`() {
            val b = buf(10, 5)
            b.setCursor(99, 0)
            assertEquals(4, b.getCursorRow())
        }

        @Test fun `setCursor clamps col below zero`() {
            val b = buf()
            b.setCursor(0, -1)
            assertEquals(0, b.getCursorCol())
        }

        @Test fun `setCursor clamps col above width`() {
            val b = buf(10, 5)
            b.setCursor(0, 99)
            assertEquals(9, b.getCursorCol())
        }

        @Test fun `moveCursor applies delta`() {
            val b = buf(10, 5)
            b.setCursor(2, 3)
            b.moveCursor(1, 2)
            assertEquals(3, b.getCursorRow())
            assertEquals(5, b.getCursorCol())
        }

        @Test fun `moveCursor clamps at boundaries`() {
            val b = buf(10, 5)
            b.setCursor(0, 0)
            b.moveCursor(-10, -10)
            assertEquals(0, b.getCursorRow())
            assertEquals(0, b.getCursorCol())
        }
    }

    // ── Attributes ────────────────────────────────────────────────────────────

    @Nested inner class Attributes {

        @Test fun `setAttributes updates currentAttributes`() {
            val b = buf()
            val attrs = TextAttributes(fg = Color.RED, bg = Color.BLUE, bold = true)
            b.setAttributes(attrs)
            assertEquals(attrs, b.currentAttributes)
        }

        @Test fun `attributes are captured into cell at write time`() {
            val b = buf()
            val attrs = TextAttributes(
                fg = Color.GREEN, bg = Color.YELLOW,
                bold = true, italic = true, underline = true,
            )
            b.setAttributes(attrs)
            b.writeText("X")
            val cell = b.readCell(0, 0)
            assertEquals('X',          cell.char)
            assertEquals(Color.GREEN,  cell.fg)
            assertEquals(Color.YELLOW, cell.bg)
            assertTrue(cell.bold)
            assertTrue(cell.italic)
            assertTrue(cell.underline)
        }

        @Test fun `changing attributes after write does not affect existing cells`() {
            val b = buf()
            b.setAttributes(TextAttributes(fg = Color.CYAN))
            b.writeText("A")
            b.setAttributes(TextAttributes(fg = Color.RED))
            assertEquals(Color.CYAN, b.readCell(0, 0).fg)
        }

        @Test fun `default attributes produce blank cell`() {
            val b = buf()
            b.writeText(" ")
            assertEquals(Cell.BLANK, b.readCell(0, 0))
        }
    }

    // ── writeText ─────────────────────────────────────────────────────────────

    @Nested inner class WriteText {

        @Test fun `writes single character at cursor`() {
            val b = buf(5, 3)
            b.writeText("A")
            assertEquals('A', b.readCell(0, 0).char)
        }

        @Test fun `advances cursor after write`() {
            val b = buf(5, 3)
            b.writeText("AB")
            assertEquals(0, b.getCursorRow())
            assertEquals(2, b.getCursorCol())
        }

        @Test fun `wraps cursor to next line at right edge`() {
            val b = buf(3, 3)
            b.writeText("ABCD")
            assertEquals("ABC\nD  \n   ", b.readScreen())
            assertEquals(1, b.getCursorRow())
            assertEquals(1, b.getCursorCol())
        }

        @Test fun `overwrites existing content`() {
            val b = buf(5, 3)
            b.writeText("HELLO")
            b.setCursor(0, 2)
            b.writeText("X")
            assertEquals("HEXLO", b.readLine(0))
        }

        @Test fun `scrolls up when writing past last row`() {
            val b = buf(3, 2)
            b.writeText("ABCDEF")    // fills both rows exactly
            assertEquals("ABC\nDEF", b.readScreen())
            b.writeText("G")         // triggers scroll
            assertEquals("DEF\nG  ", b.readScreen())
        }

        @Test fun `scrolled-off line goes to scrollback`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")
            assertEquals(1, b.scrollbackSize())
            assertTrue(b.readAll().startsWith("ABC"))
        }
    }

    // ── insertText ────────────────────────────────────────────────────────────

    @Nested inner class InsertText {

        @Test fun `inserts character shifting line right`() {
            val b = buf(5, 3)
            b.writeText("BCDE")
            b.setCursor(0, 0)
            b.insertText("A")
            assertEquals("ABCDE", b.readLine(0))
        }

        @Test fun `overflow cascades to next line`() {
            val b = buf(3, 3)
            b.writeText("ABCDE")    // row0="ABC", row1="DE "
            b.setCursor(0, 0)
            b.insertText("X")       // row0="XAB", overflow 'C' -> row1 col0
            assertEquals("XAB", b.readLine(0))
            assertEquals("CDE", b.readLine(1))
        }

        @Test fun `overflow past last line is discarded`() {
            val b = buf(3, 2)
            b.writeText("ABCDEF")   // row0="ABC", row1="DEF"
            b.setCursor(0, 0)
            b.insertText("X")       // 'F' at end of last line is lost
            assertEquals("XAB", b.readLine(0))
            assertEquals("CDE", b.readLine(1))
        }

        @Test fun `cursor advances after insert`() {
            val b = buf(5, 3)
            b.insertText("AB")
            assertEquals(0, b.getCursorRow())
            assertEquals(2, b.getCursorCol())
        }
    }

    // ── fillLine ──────────────────────────────────────────────────────────────

    @Nested inner class FillLine {

        @Test fun `fills line with given character`() {
            val b = buf(4, 3)
            b.fillLine(1, '-')
            assertEquals("    \n----\n    ", b.readScreen())
        }

        @Test fun `fills line with spaces by default`() {
            val b = buf(4, 3)
            b.writeText("ABCD")
            b.fillLine(0)
            assertEquals("    ", b.readLine(0))
        }

        @Test fun `fill uses currentAttributes`() {
            val b = buf(3, 2)
            b.setAttributes(TextAttributes(fg = Color.MAGENTA, bold = true))
            b.fillLine(0, 'X')
            val cell = b.readCell(0, 0)
            assertEquals('X',           cell.char)
            assertEquals(Color.MAGENTA, cell.fg)
            assertTrue(cell.bold)
        }

        @Test fun `row is clamped`() {
            val b = buf(3, 3)
            b.fillLine(99, 'X')    // clamps to row 2
            assertEquals("XXX", b.readLine(2))
        }
    }

    // ── insertLineAtBottom ────────────────────────────────────────────────────

    @Nested inner class InsertLineAtBottom {

        @Test fun `top line scrolls into scrollback`() {
            val b = buf(3, 2)
            b.writeText("ABC")
            b.insertLineAtBottom()
            assertEquals(1, b.scrollbackSize())
            assertTrue(b.readAll().contains("ABC"))
        }

        @Test fun `blank line appears at bottom`() {
            val b = buf(3, 2)
            b.writeText("ABCDEF")
            b.insertLineAtBottom()
            assertEquals("   ", b.readLine(1))
        }

        @Test fun `content shifts up`() {
            val b = buf(3, 3)
            b.writeText("ABCDEF")
            b.insertLineAtBottom()
            assertEquals("DEF", b.readLine(0))
            assertEquals("   ", b.readLine(1))
        }
    }

    // ── clearScreen ───────────────────────────────────────────────────────────

    @Nested inner class ClearScreen {

        @Test fun `clears all cells`() {
            val b = buf(3, 2)
            b.writeText("ABCDEF")
            b.clearScreen()
            assertEquals("   \n   ", b.readScreen())
        }

        @Test fun `resets cursor to origin`() {
            val b = buf()
            b.setCursor(3, 7)
            b.clearScreen()
            assertEquals(0, b.getCursorRow())
            assertEquals(0, b.getCursorCol())
        }

        @Test fun `preserves scrollback`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")
            val sbSize = b.scrollbackSize()
            b.clearScreen()
            assertEquals(sbSize, b.scrollbackSize())
        }
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Nested inner class ClearAll {

        @Test fun `clears screen and scrollback`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")
            b.clearAll()
            assertEquals("   \n   ", b.readScreen())
            assertEquals(0, b.scrollbackSize())
        }
    }

    // ── Read — screen ─────────────────────────────────────────────────────────

    @Nested inner class ReadScreen {

        @Test fun `readCell returns correct cell`() {
            val b = buf(5, 3)
            b.setAttributes(TextAttributes(fg = Color.BLUE))
            b.setCursor(1, 3)
            b.writeText("Z")
            val cell = b.readCell(1, 3)
            assertEquals('Z',        cell.char)
            assertEquals(Color.BLUE, cell.fg)
        }

        @Test fun `readLine returns line as string`() {
            val b = buf(5, 3)
            b.writeText("HELLO")
            assertEquals("HELLO", b.readLine(0))
        }

        @Test fun `readScreen returns all rows joined by newline`() {
            val b = buf(3, 2)
            b.writeText("ABCDEF")
            assertEquals("ABC\nDEF", b.readScreen())
        }
    }

    // ── Read — scrollback ─────────────────────────────────────────────────────

    @Nested inner class ReadScrollback {

        @Test fun `readScrollbackLine returns scrolled-off content`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")   // "ABC" scrolls off
            assertEquals("ABC", b.readScrollbackLine(0))
        }

        @Test fun `readScrollbackCell returns correct cell and attributes`() {
            val b = buf(3, 2)
            b.setAttributes(TextAttributes(fg = Color.RED))
            b.writeText("ABC")
            b.setAttributes(TextAttributes())
            b.insertLineAtBottom()   // "ABC" -> scrollback
            val cell = b.readScrollbackCell(0, 1)
            assertEquals('B',       cell.char)
            assertEquals(Color.RED, cell.fg)
        }

        @Test fun `readAll prepends scrollback to screen`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")   // "ABC" scrolls off
            assertEquals("ABC\nDEF\nG  ", b.readAll())
        }
    }

    // ── Read — global coordinate space ────────────────────────────────────────

    @Nested inner class ReadGlobal {

        @Test fun `totalLines equals scrollbackSize plus height`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")
            assertEquals(b.scrollbackSize() + b.height, b.totalLines)
        }

        @Test fun `readGlobalLine addresses scrollback rows first`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")   // scrollback=["ABC"], screen=["DEF","G  "]
            assertEquals("ABC", b.readGlobalLine(0))
            assertEquals("DEF", b.readGlobalLine(1))
            assertEquals("G  ", b.readGlobalLine(2))
        }

        @Test fun `readGlobalCell returns correct cell in scrollback`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")
            assertEquals('B', b.readGlobalCell(0, 1).char)
        }

        @Test fun `readGlobalCell returns correct cell in screen`() {
            val b = buf(3, 2)
            b.writeText("ABCDEFG")
            assertEquals('E', b.readGlobalCell(1, 1).char)
        }
    }

    // ── Scrollback bounds ─────────────────────────────────────────────────────

    @Nested inner class Scrollback {

        @Test fun `bounded scrollback drops oldest line`() {
            val b = TerminalBuffer(3, 2, maxScrollback = 2)
            b.writeText("ABCDEFGHIJKLMNO")
            assertEquals(2, b.scrollbackSize())
        }

        @Test fun `scrollback disabled when maxScrollback is zero`() {
            val b = TerminalBuffer(3, 2, maxScrollback = 0)
            b.writeText("ABCDEFG")
            assertEquals(0, b.scrollbackSize())
        }

        @Test fun `scrollback line is immutable history`() {
            val b = buf(3, 2)
            b.writeText("ABC")
            b.insertLineAtBottom()
            b.setCursor(0, 0)
            b.writeText("XYZ")
            assertTrue(b.readAll().startsWith("ABC"), "scrollback was mutated")
        }

        @Test fun `scrollback order is oldest-first`() {
            val b = buf(3, 2)
            b.writeText("AAA")
            b.insertLineAtBottom()
            b.setCursor(0, 0)
            b.writeText("BBB")
            b.insertLineAtBottom()
            val lines = b.readAll().split("\n")
            assertEquals("AAA", lines[0])
            assertEquals("BBB", lines[1])
        }
    }

    // ── Screen invariants ─────────────────────────────────────────────────────

    @Nested inner class ScreenInvariants {

        @Test fun `screen always has exactly height lines`() {
            val b = buf(3, 4)
            repeat(20) { b.writeText("X") }
            assertEquals(4, b.readScreen().split("\n").size)
        }

        @Test fun `every line always has exactly width cells`() {
            val b = buf(5, 3)
            repeat(30) { b.writeText("X") }
            b.readScreen().split("\n").forEach { assertEquals(5, it.length) }
        }

        @Test fun `cursor never leaves bounds after many writes`() {
            val b = buf(3, 3)
            repeat(100) { b.writeText("X") }
            assertTrue(b.getCursorRow() in 0 until 3)
            assertTrue(b.getCursorCol() in 0 until 3)
        }
    }
}
