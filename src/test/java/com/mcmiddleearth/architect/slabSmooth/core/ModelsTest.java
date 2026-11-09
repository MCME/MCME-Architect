package com.mcmiddleearth.architect.slabSmooth.core;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelsTest {

    private static final int UP = 4; // Octants direction +y
    private static final int COLOUR = 0x406080;

    /** A texture wider than MAX_TEXTURE_SIDE has no colour, like a missing one; its size is read before its pixels. */
    @Test
    void aTextureOverTheLimitIsIgnored() throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        cube(files, "stone", "block/too_wide");
        cube(files, "dirt", "block/widest");
        cube(files, "sand", "block/missing");
        files.put("assets/minecraft/textures/block/too_wide.png", png(Models.MAX_TEXTURE_SIDE + 1, 1));
        files.put("assets/minecraft/textures/block/widest.png", png(Models.MAX_TEXTURE_SIDE, 1));
        Models models = new Models(files::get);

        Models.Face missing = models.face("minecraft:sand", UP), tooWide = models.face("minecraft:stone", UP);
        assertEquals(-1, missing.rgb(), "a missing texture has no colour");
        assertEquals("minecraft:block/too_wide", tooWide.texture());
        assertEquals(missing.rgb(), tooWide.rgb(), "too wide: no colour, as if missing");
        assertEquals(missing.contrast(), tooWide.contrast());
        assertEquals(COLOUR, models.face("minecraft:dirt", UP).rgb(), "at the limit it is still read");
    }

    /** A full cube showing the texture on top. */
    private static void cube(Map<String, byte[]> files, String block, String texture) {
        files.put("assets/minecraft/blockstates/" + block + ".json",
                ("{\"variants\": {\"\": {\"model\": \"block/" + block + "\"}}}").getBytes(StandardCharsets.UTF_8));
        files.put("assets/minecraft/models/block/" + block + ".json", ("{\"textures\": {\"all\": \"" + texture + "\"},"
                + " \"elements\": [{\"from\": [0, 0, 0], \"to\": [16, 16, 16], \"faces\": {\"up\": {\"texture\": \"#all\"}}}]}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF000000 | COLOUR);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
