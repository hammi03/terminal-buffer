# terminal-buffer

A fixed-size terminal text buffer with bounded scrollback, wide-character support, and resize — implemented in pure Kotlin.

## Data model

```
Color            enum of 17 values: DEFAULT + 16 standard ANSI terminal colours
TextAttributes   current write state: fg, bg, bold, italic, underline
Cell             immutable grid cell: char + snapshot of TextAttributes + isContinuation flag
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
| `resize(newWidth, newHeight)` | Change buffer dimensions (see below) |

## Bonus features

### Wide characters

Characters such as CJK ideographs (e.g. `中`, U+4E2D) occupy two terminal columns. The buffer handles them as follows:

- `CharWidth.of(ch)` returns `2` for East Asian wide characters, `1` for all others, based on Unicode block ranges.
- Writing a wide character places the character in the lead cell and a `Cell.CONTINUATION` marker in the adjacent right cell. Both cells carry the same `TextAttributes`.
- Cursor advances by 2 after a wide character.
- If a wide character would split across the right edge of a line, the last column is filled with a blank and the wide character is written at the start of the next line.
- Overwriting either half of a wide character automatically clears the other half to prevent orphaned half-width artefacts.
- Wide characters work in both overwrite and insert mode.

**Known limitation:** Supplementary codepoints above U+FFFF (many emoji, rare CJK Extension B/C/D characters) require surrogate pairs on the JVM and are not handled. They arrive as two separate `Char` values and are each treated as width-1 characters. Full support would require a codepoint-aware write API (`writeCodePoint(Int)`).

### Resize

`resize(newWidth, newHeight)` changes the buffer dimensions.

**Width:**
- Grow → blank cells appended on the right of every line (screen and scrollback).
- Shrink → cells beyond the new width are discarded. Wide characters straddling the new right edge may become orphaned (noted as a known limitation; a production implementation would scan and clear such cells).

**Height:**
- Grow → blank rows appended at the bottom of the visible screen.
- Shrink → the top `oldHeight − newHeight` rows are pushed into scrollback before the screen is trimmed. This mirrors normal scroll behaviour and preserves content as history rather than silently discarding it.

The cursor is clamped to the new bounds after every resize.

## Semantic decisions

**Insert mode overflow**
Content pushed off the right edge of a line cascades to the beginning of the next line, all the way to the bottom. Content that would overflow past the last row is discarded — insert mode does not trigger a scroll. This matches classic VT100 ICH behaviour, where insertion operates within the current page.

**Global coordinate space**
`readGlobalLine(0)` is the oldest scrollback line; `readGlobalLine(totalLines - 1)` is the bottom row of the visible screen. This gives uniform access to the full buffer history without separate scrollback vs screen indexing at the call site.

**Colours**
Foreground and background are typed as `Color`, a 17-value enum covering the 16 standard ANSI terminal colours plus `DEFAULT` (the terminal's own default, and the zero value so that freshly constructed cells carry no explicit colour).

## Possible improvements

- **Surrogate-pair wide chars**: add a `writeText(codePoints: IntArray)` overload that processes codepoints rather than `Char` values, enabling correct handling of emoji and supplementary CJK characters.
- **Wide-char boundary fix on resize**: after a width-shrink, scan the new rightmost column of each line and clear any LEAD cell whose CONTINUATION was just cut off.
- **Insert mode scroll**: optionally scroll the buffer when insert-mode overflow reaches the last line, instead of discarding it — useful for editor-like behaviour.
- **Soft wrap vs hard wrap tracking**: distinguish lines that wrapped automatically from lines that ended with an explicit newline, which matters for copy-paste and reflowing on resize.
- **Resize content reflow**: when width changes, reflow wrapped lines rather than truncating/padding each row independently.

## Build & test

```bash
./gradlew test
```

Requires JDK 17+. Open in IntelliJ IDEA — Gradle imports automatically.
