package com.mcmiddleearth.architect.additionalCommands;

import com.mcmiddleearth.pluginutil.message.MessageUtil;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

// A hover line holds a word of at most 37 characters, so help texts go to hoverFormat with longer words cut into
// pieces that fit. Plain unit tests: no server.
class HoverWordsTest {

    @Test
    void aLongWordIsCutIntoPiecesThatFitAHoverLine() {
        assertEquals("a " + "x".repeat(37) + " " + "x".repeat(37) + " " + "x".repeat(6) + " b",
                AbstractArchitectCommand.splitLongWords("a " + "x".repeat(80) + " b"));
    }

    @Test
    void shortWordsSpacesAndLineBreaksAreKept() {
        for (String text : new String[] {"one \n two", "x".repeat(37), "a  b", " lead", "trail "}) {
            assertEquals(text, AbstractArchitectCommand.splitLongWords(text), text);
        }
    }

    // With the PluginUtils that Architect builds against, a cut text is formatted with every piece on a line.
    @Test
    void aCutTextIsFormattedPieceByPiece() {
        String formatted = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> new MessageUtil()
                .hoverFormat(AbstractArchitectCommand.splitLongWords("/cmd: " + "x".repeat(60)), ":", true));

        assertTrue(formatted.contains("x".repeat(37)), formatted);
    }
}
