package com.fruit.ffmpeggui.core

class CommandParseException(message: String) : IllegalArgumentException(message)

object CommandTokenizer {
    fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false

        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }

        command.forEach { char ->
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }

                char == '\\' -> escaping = true
                quote != null -> {
                    if (char == quote) {
                        quote = null
                    } else {
                        current.append(char)
                    }
                }

                char == '\'' || char == '"' -> quote = char
                char.isWhitespace() -> flush()
                else -> current.append(char)
            }
        }

        if (escaping) {
            current.append('\\')
        }
        if (quote != null) {
            throw CommandParseException("Unclosed quote in command.")
        }
        flush()

        return tokens
    }

    fun quote(arguments: List<String>): String = arguments.joinToString(" ") { quoteArgument(it) }

    private fun quoteArgument(argument: String): String {
        if (argument.isEmpty()) return "''"
        val safe = argument.all { it.isLetterOrDigit() || it in "@%_+=:,./{}[]-" }
        if (safe) return argument
        return "'" + argument.replace("'", "'\\''") + "'"
    }
}
