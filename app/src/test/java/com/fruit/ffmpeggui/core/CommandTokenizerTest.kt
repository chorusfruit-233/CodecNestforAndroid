package com.fruit.ffmpeggui.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommandTokenizerTest {
    @Test
    fun tokenizesQuotedArguments() {
        val tokens = CommandTokenizer.tokenize("-i 'input file.mp4' -metadata title=\"My Video\" output.mp4")

        assertEquals(
            listOf("-i", "input file.mp4", "-metadata", "title=My Video", "output.mp4"),
            tokens
        )
    }

    @Test
    fun preservesEscapedSpaces() {
        val tokens = CommandTokenizer.tokenize("-i input\\ file.mp4 output.mp4")

        assertEquals(listOf("-i", "input file.mp4", "output.mp4"), tokens)
    }

    @Test
    fun rejectsUnclosedQuote() {
        assertThrows(CommandParseException::class.java) {
            CommandTokenizer.tokenize("-i 'input.mp4 output.mp4")
        }
    }

    @Test
    fun quotesUnsafeArguments() {
        val command = CommandTokenizer.quote(listOf("-i", "/tmp/input file.mp4", "out.mp4"))

        assertEquals("-i '/tmp/input file.mp4' out.mp4", command)
    }
}
