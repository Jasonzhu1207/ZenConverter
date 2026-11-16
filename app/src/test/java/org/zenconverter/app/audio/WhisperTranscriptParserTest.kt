package org.zenconverter.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTranscriptParserTest {

    @Test
    fun parsesSegmentsAndWords() {
        val json = """
            {"segments":[
              {"start":0.0,"end":2.5,"text":" hello world",
               "words":[
                 {"word":"hello","start":0.0,"end":1.0},
                 {"word":"world","start":1.0,"end":2.5}
               ]},
              {"start":2.5,"end":4.0,"text":" again","words":[]}
            ]}
        """.trimIndent()

        val segments = WhisperTranscriptParser.parse(json)

        assertEquals(2, segments.size)
        assertEquals(0.0, segments[0].startSeconds, 0.0001)
        assertEquals(2.5, segments[0].endSeconds, 0.0001)
        assertEquals("hello world", segments[0].text)
        assertEquals(2, segments[0].words.size)
        assertEquals("world", segments[0].words[1].text)
        assertEquals(1.5, segments[0].words[1].durationSeconds, 0.0001)
    }

    @Test
    fun toleratesWordsWithoutATimingEntry() {
        val json = """{"segments":[{"start":0,"end":1,"text":" hi",
            "words":[{"word":"hi","start":0,"end":1},{"word":"","start":0,"end":1}]}]}"""

        val segments = WhisperTranscriptParser.parse(json)

        assertEquals(1, segments.size)
        assertEquals(1, segments[0].words.size)
        assertEquals("hi", segments[0].words[0].text)
    }

    @Test
    fun skipsSegmentsWithAnInvertedRange() {
        val json = """{"segments":[
            {"start":5,"end":2,"text":"bad"},
            {"start":0,"end":1,"text":"good"}
        ]}"""

        val segments = WhisperTranscriptParser.parse(json)

        assertEquals(1, segments.size)
        assertEquals("good", segments[0].text)
    }

    @Test
    fun returnsEmptyForMissingOrMalformedInput() {
        assertTrue(WhisperTranscriptParser.parse(null).isEmpty())
        assertTrue(WhisperTranscriptParser.parse("").isEmpty())
        assertTrue(WhisperTranscriptParser.parse("not json").isEmpty())
        assertTrue(WhisperTranscriptParser.parse("""{"other":1}""").isEmpty())
    }

    @Test
    fun toPlainTextJoinsTrimmedSegments() {
        val segments = listOf(
            TranscriptSegment(0.0, 1.0, "hello"),
            TranscriptSegment(1.0, 2.0, "world")
        )
        assertEquals("hello world", WhisperTranscriptParser.toPlainText(segments))
    }
}
