package com.jcapps.adbremote.utils

import com.jcapps.adbremote.model.VirtualKey

// HID usage ranges (letters a..z, digits 1..0). RECON-UNSURE: names; unused in the app, kept by R8.
// obf: C2437 <clinit> C3630(4, 29, 1)
val letterKeyRange = 4..29

// obf: C2437 <clinit> C3630(30, 39, 1)
val numberKeyRange = 30..39

// Shifted HID keys (225 = Left Shift). RECON-UNSURE: val names.
// obf: f9372
val KEY_EXCLAMATION = VirtualKey(30, 225)

// obf: f9369
val KEY_AT = VirtualKey(31, 225)

// obf: f9380
val KEY_HASH = VirtualKey(32, 225)

// obf: f9376
val KEY_DOLLAR = VirtualKey(33, 225)

// obf: f9385
val KEY_PERCENT = VirtualKey(34, 225)

// obf: f9382
val KEY_CARET = VirtualKey(35, 225)

// obf: f9375
val KEY_AMPERSAND = VirtualKey(36, 225)

// obf: f9379
val KEY_ASTERISK = VirtualKey(37, 225)

// obf: f9367
val KEY_LEFT_PARENTHESIS = VirtualKey(38, 225)

// obf: f9370
val KEY_RIGHT_PARENTHESIS = VirtualKey(39, 225)

// obf: f9371
val KEY_UNDERSCORE = VirtualKey(45, 225)

// obf: f9381
val KEY_PLUS = VirtualKey(46, 225)

// obf: f9368
val KEY_LEFT_BRACE = VirtualKey(47, 225)

// obf: f9374
val KEY_RIGHT_BRACE = VirtualKey(48, 225)

// obf: f9378
val KEY_PIPE = VirtualKey(49, 225)

// obf: f9384
val KEY_COLON = VirtualKey(51, 225)

// obf: f9377
val KEY_QUOTE = VirtualKey(52, 225)

// obf: f9383
val KEY_TILDE = VirtualKey(53, 225)

// obf: f9386
val KEY_LESS_THAN = VirtualKey(54, 225)

// obf: f9373
val KEY_GREATER_THAN = VirtualKey(55, 225)

// obf: f9387
val KEY_QUESTION = VirtualKey(56, 225)

/** Maps every character to its US-layout HID key; throws on unsupported characters. */
// obf: m4681
@Throws(UnsupportedCharacterException::class)
fun String.toVirtualKeys(): List<VirtualKey> {
    val passed = StringBuilder()
    val keys = ArrayList<VirtualKey>(length)
    for (char in this) {
        val key = when (char) {
            '\t' -> VirtualKey(43)
            '\n' -> VirtualKey(40)
            ' ' -> VirtualKey(44)
            '!' -> KEY_EXCLAMATION
            '"' -> KEY_QUOTE
            '#' -> KEY_HASH
            '$' -> KEY_DOLLAR
            '%' -> KEY_PERCENT
            '&' -> KEY_AMPERSAND
            '\'' -> VirtualKey(52)
            '(' -> KEY_LEFT_PARENTHESIS
            ')' -> KEY_RIGHT_PARENTHESIS
            '*' -> KEY_ASTERISK
            '+' -> KEY_PLUS
            ',' -> VirtualKey(54)
            '-' -> VirtualKey(45)
            '.' -> VirtualKey(55)
            '/' -> VirtualKey(56)
            '0' -> VirtualKey(39)
            in '1'..'9' -> VirtualKey(char - '1' + 30)
            ':' -> KEY_COLON
            ';' -> VirtualKey(51)
            '<' -> KEY_LESS_THAN
            '=' -> VirtualKey(46)
            '>' -> KEY_GREATER_THAN
            '?' -> KEY_QUESTION
            '@' -> KEY_AT
            in 'A'..'Z' -> VirtualKey(char - 'A' + 4, 225)
            '[' -> VirtualKey(47)
            '\\' -> VirtualKey(49)
            ']' -> VirtualKey(48)
            '^' -> KEY_CARET
            '_' -> KEY_UNDERSCORE
            '`' -> VirtualKey(53)
            in 'a'..'z' -> VirtualKey(char - 'a' + 4)
            '{' -> KEY_LEFT_BRACE
            '|' -> KEY_PIPE
            '}' -> KEY_RIGHT_BRACE
            '~' -> KEY_TILDE
            else -> throw UnsupportedCharacterException(
                char,
                passed.toString(),
                "character '$char' (codePoint: ${char.code}) is not supported.",
            )
        }
        passed.append(char)
        keys.add(key)
    }
    return keys
}

/**
 * Splits [str] into Android key codes (Int, for characters `input keyevent` can type) and
 * single-character Strings (sent with `input text`); throws on unsupported characters.
 * RECON-UNSURE name.
 */
// obf: AbstractC0395.m2054
@Throws(UnsupportedCharacterException::class)
fun toKeyEvents(str: String): List<Any> {
    val passed = StringBuilder()
    val events = ArrayList<Any>(str.length)
    for (char in str) {
        val event: Any = when {
            char == '\t' -> 61
            char == '\n' -> 66
            char == ' ' -> 62
            char in '!'..'&' -> char.toString()
            char == '\'' -> 75
            char == '(' -> 162
            char == ')' -> 163
            char in '*'..'+' -> char.toString()
            char == ',' -> 55
            char == '-' -> 69
            char == '.' -> 158
            char == '/' -> 76
            char in '0'..'9' -> char - ')'
            char == ':' -> char.toString()
            char == ';' -> 74
            char == '<' -> char.toString()
            char == '=' -> 70
            char in '>'..'?' -> char.toString()
            char == '@' -> 77
            char in 'A'..'Z' -> char.toString()
            char == '[' -> 71
            char == '\\' -> 73
            char == ']' -> 72
            char in '^'..'_' -> char.toString()
            char == '`' -> 68
            char in 'a'..'z' -> char - 'D'
            char in '{'..'~' -> char.toString()
            else -> throw UnsupportedCharacterException(
                char,
                passed.toString(),
                "character '$char' (codePoint: ${char.code}) is not supported.",
            )
        }
        passed.append(char)
        events.add(event)
    }
    return events
}
