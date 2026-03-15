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

    // ── Wide characters ───────────────────────────────────────────────────────

    @Nested inner class WideCharacters {

        // U+4E2D (中) is a CJK ideograph — display width 2
        private val WIDE = '\u4E2D'

        @Test fun `CharWidth returns 2 for CJK character`() {
            assertEquals(2, CharWidth.of(WIDE))
        }

        @Test fun `CharWidth returns 1 for ASCII`() {
            assertEquals(1, CharWidth.of('A'))
        }

        @Test fun `wide char occupies two columns — lead cell has char`() {
            val b = buf(6, 2)
            b.writeText("$WIDE")
            assertEquals(WIDE, b.readCell(0, 0).char)
        }

        @Test fun `wide char occupies two columns — second cell is continuation`() {
            val b = buf(6, 2)
            b.writeText("$WIDE")
            val cont = b.readCell(0, 1)
            assertTrue(cont.isContinuation)
            assertEquals(' ', cont.char)
        }

        @Test fun `cursor advances by 2 after wide char`() {
            val b = buf(6, 2)
            b.writeText("$WIDE")
            assertEquals(0, b.getCursorRow())
            assertEquals(2, b.getCursorCol())
        }

        @Test fun `wide char followed by ascii`() {
            val b = buf(6, 2)
            b.writeText("${WIDE}A")
            assertEquals(WIDE, b.readCell(0, 0).char)
            assertEquals('A',  b.readCell(0, 2).char)
        }

        @Test fun `wide char at last column wraps and fills last col with blank`() {
            val b = buf(3, 2)   // width=3; wide char at col 2 won't fit
            b.setCursor(0, 2)
            b.writeText("$WIDE")
            // col 2 should be blank (can't fit half a wide char)
            assertEquals(' ', b.readCell(0, 2).char)
            assertFalse(b.readCell(0, 2).isContinuation)
            // wide char on next line
            assertEquals(WIDE, b.readCell(1, 0).char)
            assertTrue(b.readCell(1, 1).isContinuation)
        }

        @Test fun `overwriting continuation clears lead`() {
            val b = buf(6, 2)
            b.writeText("$WIDE")       // cols 0-1
            b.setCursor(0, 1)
            b.writeText("X")           // overwrite continuation at col 1
            // col 0 (old lead) must be cleared to blank
            assertEquals(' ',  b.readCell(0, 0).char)
            assertFalse(b.readCell(0, 0).isContinuation)
            assertEquals('X',  b.readCell(0, 1).char)
        }

        @Test fun `overwriting lead clears continuation`() {
            val b = buf(6, 2)
            b.writeText("$WIDE")       // cols 0-1
            b.setCursor(0, 0)
            b.writeText("X")           // overwrite lead at col 0
            assertEquals('X',  b.readCell(0, 0).char)
            // col 1 (old continuation) must be cleared
            assertEquals(' ',  b.readCell(0, 1).char)
            assertFalse(b.readCell(0, 1).isContinuation)
        }

        @Test fun `wide chars in insert mode shift content`() {
            val b = buf(6, 2)
            b.writeText("AB")          // col 0='A', col 1='B'
            b.setCursor(0, 0)
            b.insertText("$WIDE")      // insert wide char before A
            assertEquals(WIDE, b.readCell(0, 0).char)
            assertTrue(b.readCell(0, 1).isContinuation)
            assertEquals('A',  b.readCell(0, 2).char)
            assertEquals('B',  b.readCell(0, 3).char)
        }

        @Test fun `multiple wide chars advance cursor correctly`() {
            val b = buf(8, 2)
            b.writeText("$WIDE$WIDE$WIDE")
            assertEquals(0, b.getCursorRow())
            assertEquals(6, b.getCursorCol())
        }
    }

    // ── Resize ────────────────────────────────────────────────────────────────

    @Nested inner class Resize {

        @Test fun `grow width adds blank cells on right`() {
            val b = buf(3, 2)
            b.writeText("ABC")
            b.resize(5, 2)
            assertEquals(5, b.width)
            assertEquals("ABC  ", b.readLine(0))
        }

        @Test fun `shrink width truncates cells`() {
            val b = buf(5, 2)
            b.writeText("HELLO")
            b.resize(3, 2)
            assertEquals(3, b.width)
            assertEquals("HEL", b.readLine(0))
        }

        @Test fun `grow height adds blank rows at bottom`() {
            val b = buf(3, 2)
            b.writeText("ABCDEF")
            b.resize(3, 4)
            assertEquals(4, b.height)
            assertEquals("ABC", b.readLine(0))
            assertEquals("DEF", b.readLine(1))
            assertEquals("   ", b.readLine(2))
            assertEquals("   ", b.readLine(3))
        }

        @Test fun `shrink height pushes top rows into scrollback`() {
            val b = buf(3, 4)
            b.writeText("ABCDEFGHIJKL")   // fills all 4 rows
            b.resize(3, 2)
            assertEquals(2, b.height)
            // top 2 rows (ABC, DEF) moved to scrollback
            assertEquals(2, b.scrollbackSize())
            assertEquals("ABC", b.readScrollbackLine(0))
            assertEquals("DEF", b.readScrollbackLine(1))
            // bottom 2 rows remain visible
            assertEquals("GHI", b.readLine(0))
            assertEquals("JKL", b.readLine(1))
        }

        @Test fun `cursor is clamped after shrink`() {
            val b = buf(5, 5)
            b.setCursor(4, 4)
            b.resize(3, 3)
            assertTrue(b.getCursorRow() <= 2)
            assertTrue(b.getCursorCol() <= 2)
        }

        @Test fun `resize updates width and height properties`() {
            val b = buf(4, 4)
            b.resize(7, 3)
            assertEquals(7, b.width)
            assertEquals(3, b.height)
        }

        @Test fun `resize to same dimensions is a no-op`() {
            val b = buf(3, 2)
            b.writeText("ABCDEF")
            b.resize(3, 2)
            assertEquals("ABC\nDEF", b.readScreen())
        }

        @Test fun `invalid resize dimensions throw`() {
            val b = buf()
            assertThrows(IllegalArgumentException::class.java) { b.resize(0, 5) }
            assertThrows(IllegalArgumentException::class.java) { b.resize(5, 0) }
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
