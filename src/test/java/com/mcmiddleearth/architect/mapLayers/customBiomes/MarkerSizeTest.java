package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.MapShape;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

// The map serves each world's marker file gzip-compressed at level 1, and every visit downloads it whole, so a world is
// held back when its markers would add more than the target. The estimate is the compressed size of the markers as
// dynmap's file holds them, checked here against the JDK's own gzip stream.
class MarkerSizeTest {

    private static final String BACKSLASH = "\\";

    /** The size of the text gzipped at level 1 by a stream, as a server sends a file. */
    private static int gzipLevelOne(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes) {
            {
                def.setLevel(Deflater.BEST_SPEED);
            }
        }) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.size();
    }

    private static MapShape.Area forest(String description) {
        return new MapShape.Area("cb.world.0.-2.cbc:forest", "Old forest + cbc:fireflies", description,
                new MapShape.Style(0x3cb44b, 0.6, 2, 0xe6194b, 0.3), "world", new double[]{0, 4, 4, 0},
                new double[]{-8, -8, -4, -4});
    }

    private static String letters(Random random, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append((char) ('a' + random.nextInt(26)));
        }
        return text.toString();
    }

    @Test
    void theEstimateIsTheGzipLevelOneSizeOfTheMarkerJson() throws IOException {
        // a label with quotes, a slash and a letter beyond ASCII; a window with a tab, a backslash and a line break
        MapShape.Area fen = new MapShape.Area("cb.world.5.7.cbc:fen", "Fen \"&\" Marsh / L" + (char) 0xF3 + "rien",
                "<i>Fen</i>\tby\\the\nsea", new MapShape.Style(0x008080, 0.01, 3, 0x008080, 0.25), "world",
                new double[]{20, 22, 22, 24, 24, 20}, new double[]{28, 28, 30, 30, 32, 32});
        List<MapShape.Area> areas = List.of(forest("<b>Old forest</b> <code>cbc:forest</code>"), fen);
        String json = "{\"areas\":{"
                + "\"cb.world.0.-2.cbc:forest\":{\"fillcolor\":\"#e6194b\",\"ytop\":64.0,\"color\":\"#3cb44b\","
                + "\"markup\":false,\"x\":[0.0,4.0,4.0,0.0],\"weight\":2,\"z\":[-8.0,-8.0,-4.0,-4.0],"
                + "\"ybottom\":64.0,\"label\":\"Old forest + cbc:fireflies\",\"opacity\":0.6,\"fillopacity\":0.3,"
                + "\"desc\":\"<b>Old forest<\\/b> <code>cbc:forest<\\/code>\"},"
                + "\"cb.world.5.7.cbc:fen\":{\"fillcolor\":\"#008080\",\"ytop\":64.0,\"color\":\"#008080\","
                + "\"markup\":false,\"x\":[20.0,22.0,22.0,24.0,24.0,20.0],\"weight\":3,"
                + "\"z\":[28.0,28.0,30.0,30.0,32.0,32.0],\"ybottom\":64.0,"
                + "\"label\":\"Fen \\\"&\\\" Marsh \\/ L" + BACKSLASH + "u00F3rien\",\"opacity\":0.01,"
                + "\"fillopacity\":0.25,\"desc\":\"<i>Fen<\\/i>\\tby\\\\the\\nsea\"}"
                + "}}";

        assertEquals(json, MarkerSize.json(areas));
        assertEquals(gzipLevelOne(json), MarkerSize.gzipLevel1(areas));
    }

    @Test
    void aLongerWindowGrowsTheEstimate() {
        String window = "<b>Old forest</b> <code>cbc:forest</code>";
        String longer = window + "<br/>" + letters(new Random(7), 2_000);

        int short1 = MarkerSize.gzipLevel1(List.of(forest(window)));
        int long1 = MarkerSize.gzipLevel1(List.of(forest(longer)));

        assertTrue(long1 > short1 + 1_000, () -> short1 + " and " + long1 + " bytes");
    }

    // Mainworld's layer is about 2.4 MB before compression: far more than one buffer of the compressor
    @Test
    void aLargeLayerIsMeasuredExactlyToo() throws IOException {
        Random random = new Random(11);
        List<MapShape.Area> areas = new ArrayList<>();
        for (int i = 0; i < 3_000; i++) {
            areas.add(new MapShape.Area("cb.world." + i + ".0.cbc:b" + (i % 40), letters(random, 20),
                    "<b>" + letters(random, 300) + "</b>", new MapShape.Style(0x911eb4, 0.6, 2, 0x911eb4, 0.3),
                    "world", new double[]{4 * i, 4 * i + 4, 4 * i + 4, 4 * i}, new double[]{0, 0, 4, 4}));
        }

        int size = MarkerSize.gzipLevel1(areas);

        assertEquals(gzipLevelOne(MarkerSize.json(areas)), size);
        assertTrue(MarkerSize.json(areas).length() > 1_000_000);
    }

    @Test
    void noAreasAreAnEmptyAreasObject() throws IOException {
        assertEquals("{\"areas\":{}}", MarkerSize.json(List.of()));
        assertEquals(gzipLevelOne("{\"areas\":{}}"), MarkerSize.gzipLevel1(List.of()));
    }

    @Test
    void theTargetIs512000Bytes() {
        assertEquals(512_000, MarkerSize.TARGET);
    }
}
