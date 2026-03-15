# terminal-buffer

A fixed-size terminal text buffer with bounded scrollback, implemented in pure Kotlin.

## Data model

```
TextAttributes   current write state: fg, bg, bold, italic, underline (Color enum)
Cell             immutable grid cell: char + snapshot of TextAttributes
Line             fixed-width row of Cells with insert/fill helpers
TerminalBuffer   screen (Array<Line>), scrollback (ArrayDeque<Line>), cursor, attributes
```

- `Cell` is immutable — writing replaces the reference, never mutates in place.
- Lines transferred to scrollback are never referenced by the screen again, making them effectively immutable history.
- The cursor is always clamped to valid bounds; it can never leave the visible screen.

## Operations

| Operation | Description |
|---|---|
| `setAttributes` | Set current fg, bg, bold, italic, underline |
| `setCursor` / `moveCursor` | Position cursor, clamped to bounds |
| `writeText` | Overwrite mode — replaces cells, advances cursor, scrolls at bottom |
| `insertText` | Insert mode — shifts line content right, cascades overflow to next lines |
| `fillLine` | Fill entire row with a character using current attributes |
| `insertLineAtBottom` | Scroll screen up, push top line to scrollback, blank line at bottom |
| `clearScreen` | Blank all cells, reset cursor; scrollback preserved |
| `clearAll` | Blank all cells, reset cursor, discard scrollback |
| `readCell` / `readLine` / `readScreen` | Read from visible screen |
| `readScrollbackCell` / `readScrollbackLine` | Random access into scrollback |
| `readGlobalCell` / `readGlobalLine` | Global row index across scrollback + screen |
| `readAll` | Full history string: scrollback followed by screen |

## Semantic decisions

**Insert mode overflow**
When inserting text, content pushed off the right edge of a line cascades to the beginning of the next line all the way to the bottom of the visible screen. Content that would overflow past the last row is discarded — insert mode does not trigger a scroll. This matches classic VT100 ICH behaviour.

**Global coordinate space**
`readGlobalLine(0)` is the oldest scrollback line; `readGlobalLine(totalLines - 1)` is the bottom row of the visible screen. This gives uniform access to the full buffer history without separate scrollback vs screen indexing at the call site.

**Colors**
Foreground and background are typed as `Color`, a 17-value enum covering the 16 standard ANSI terminal colours plus `DEFAULT` (the terminal's own default, and the zero value for all freshly constructed cells).

## Build & test

```bash
./gradlew test
```

Requires JDK 17+. Open in IntelliJ IDEA — Gradle imports automatically.
