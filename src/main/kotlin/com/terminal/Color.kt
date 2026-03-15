package com.terminal

/**
 * The 16 standard ANSI terminal colors plus a DEFAULT sentinel.
 *
 * DEFAULT means "use the terminal's own default colour" — it is the
 * zero value so that a freshly constructed [TextAttributes] or [Cell]
 * automatically carries the default colour without extra initialisation.
 */
enum class Color {
    DEFAULT,
    BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE,
    BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
    BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE,
}
