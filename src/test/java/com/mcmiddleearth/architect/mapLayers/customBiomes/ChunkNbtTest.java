package com.mcmiddleearth.architect.mapLayers.customBiomes;

import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.mcmiddleearth.architect.mapLayers.customBiomes.RegionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

// ChunkNbt takes a chunk's DataVersion, its Status and each section's Y and biomes out of the NBT and steps over every
// other tag by its size, because most of a chunk is block data. So the tests pin both halves: what it takes, and that
// nothing around it throws it off.
class ChunkNbtTest {

    private static List<String> ids(int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add("cbc:b" + i);
        }
        return ids;
    }

    /** 64 palette indexes that use every entry of a palette of this size (7 shares no factor with any size here). */
    private static int[] layout(int paletteSize) {
        int[] layout = new int[64];
        for (int i = 0; i < layout.length; i++) {
            layout[i] = i * 7 % paletteSize;
        }
        return layout;
    }

    private static void assertDecodes(int paletteSize, int expectedLongs) throws IOException {
        int[] layout = layout(paletteSize);
        assertEquals(paletteSize, Arrays.stream(layout).distinct().count(), "the layout uses every entry");
        long[] data = paletteSize == 1 ? null : RegionFixtures.pack(layout, paletteSize);
        if (data != null) {
            assertEquals(expectedLongs, data.length, "longs for " + paletteSize + " ids");
        }

        int[] cells = ChunkNbt.cells(ids(paletteSize), data);

        if (paletteSize == 1) {
            assertNull(cells, "a palette of one id needs no data");
        } else {
            assertArrayEquals(layout, cells, "a palette of " + paletteSize + " ids");
        }
    }

    @Test
    void palettesOfOneTwoThreeFiveAndNineIdsDecode() throws IOException {
        // 0, 1, 2, 3 and 4 bits an entry; 3 bits fit 21 entries in a long, 4 bits fit 16
        assertDecodes(1, 0);
        assertDecodes(2, 1);
        assertDecodes(3, 2);
        assertDecodes(5, 4);
        assertDecodes(9, 4);
    }

    @Test
    void palettesNeedingFiveAndSixBitsDecodeToo() throws IOException {
        assertDecodes(17, 6); // 12 entries to a long
        assertDecodes(33, 7); // 10 entries to a long
        assertDecodes(64, 7);
    }

    // Written by hand from the format, so that the helper that packs the other tests' data is not the only witness.
    @Test
    void entriesStartAtTheLowBitsOfALongAndNeverCrossIntoTheNext() throws IOException {
        // 5 ids take 3 bits: a long holds 21 entries, 63 bits of it, and entry 21 starts the second long
        long[] fiveIds = new long[4];
        fiveIds[0] = 1L | 2L << 3 | 4L << 60; // entries 0, 1 and 20
        fiveIds[1] = 3L; // entry 21
        fiveIds[3] = 2L; // entry 63
        int[] expected = new int[64];
        expected[0] = 1;
        expected[1] = 2;
        expected[20] = 4;
        expected[21] = 3;
        expected[63] = 2;

        assertArrayEquals(expected, ChunkNbt.cells(ids(5), fiveIds));

        // 9 ids take 4 bits: 16 entries to a long, and the last of them sets the sign bit
        long[] nineIds = new long[4];
        nineIds[0] = 0x21L | 8L << 60; // entries 0, 1 and 15
        nineIds[1] = 5L; // entry 16
        expected = new int[64];
        expected[0] = 1;
        expected[1] = 2;
        expected[15] = 8;
        expected[16] = 5;

        assertArrayEquals(expected, ChunkNbt.cells(ids(9), nineIds));
    }

    @Test
    void biomeDataOfTheWrongLengthIsRefused() {
        List<String> palette = ids(3); // 2 bits, so 32 entries to a long and 2 longs
        long[] tooShort = new long[1];
        long[] tooLong = new long[3];

        assertThrows(IOException.class, () -> ChunkNbt.cells(palette, tooShort));
        assertThrows(IOException.class, () -> ChunkNbt.cells(palette, tooLong));
        assertThrows(IOException.class, () -> ChunkNbt.cells(palette, null));
        assertDoesNotThrow(() -> ChunkNbt.cells(palette, new long[2]));
    }

    @Test
    void anIndexOutsideThePaletteIsRefused() {
        // 3 ids take 2 bits, which can say 3: entry 0 here
        assertThrows(IOException.class, () -> ChunkNbt.cells(ids(3), new long[] {3L, 0L}));
    }

    @Test
    void aPaletteOfOneIgnoresWhateverDataComesWithIt() throws IOException {
        assertNull(ChunkNbt.cells(ids(1), new long[] {5L}));
    }

    @Test
    void anEmptyPaletteIsRefused() {
        assertThrows(IOException.class, () -> ChunkNbt.cells(List.of(), null));
    }

    /** One tag of every kind, and lists of the kinds that are laid out differently, nested as the game nests them. */
    private static void everyKindOfTag(DataOutputStream out, String prefix) throws IOException {
        tag(out, BYTE, prefix + "Byte");
        out.writeByte(-1);
        tag(out, SHORT, prefix + "Short");
        out.writeShort(-2);
        tag(out, INT, prefix + "Int");
        out.writeInt(-3);
        tag(out, LONG, prefix + "Long");
        out.writeLong(-4);
        tag(out, FLOAT, prefix + "Float");
        out.writeFloat(5.5f);
        tag(out, DOUBLE, prefix + "Double");
        out.writeDouble(6.5);
        tag(out, BYTE_ARRAY, prefix + "Bytes");
        out.writeInt(3);
        out.write(new byte[3]);
        tag(out, STRING, prefix + "String");
        out.writeUTF("some text");
        tag(out, INT_ARRAY, prefix + "Ints");
        out.writeInt(2);
        out.writeInt(1);
        out.writeInt(2);
        tag(out, LONG_ARRAY, prefix + "Longs");
        out.writeInt(2);
        out.writeLong(1);
        out.writeLong(2);
        tag(out, LIST, prefix + "Shorts"); // elements of a fixed size
        out.writeByte(SHORT);
        out.writeInt(3);
        out.writeShort(1);
        out.writeShort(2);
        out.writeShort(3);
        tag(out, LIST, prefix + "Strings"); // elements that each carry their length
        out.writeByte(STRING);
        out.writeInt(2);
        out.writeUTF("one");
        out.writeUTF("");
        tag(out, LIST, prefix + "Arrays");
        out.writeByte(LONG_ARRAY);
        out.writeInt(2);
        out.writeInt(1);
        out.writeLong(9);
        out.writeInt(0);
        tag(out, LIST, prefix + "Lists"); // a list of lists, one of ints and one that is empty
        out.writeByte(LIST);
        out.writeInt(2);
        out.writeByte(INT);
        out.writeInt(2);
        out.writeInt(1);
        out.writeInt(2);
        out.writeByte(END);
        out.writeInt(0);
        tag(out, LIST, prefix + "Nothing");
        out.writeByte(END);
        out.writeInt(0);
        tag(out, LIST, prefix + "Compounds");
        out.writeByte(COMPOUND);
        out.writeInt(2);
        tag(out, STRING, "id");
        out.writeUTF("minecraft:chest");
        tag(out, COMPOUND, "nested");
        tag(out, LIST, "deeper");
        out.writeByte(COMPOUND);
        out.writeInt(1);
        tag(out, BYTE, "x");
        out.writeByte(1);
        out.writeByte(END);
        out.writeByte(END);
        out.writeByte(END);
        out.writeByte(END); // the second compound is empty
        tag(out, COMPOUND, prefix + "Compound");
        tag(out, LONG_ARRAY, "Heightmap");
        out.writeInt(1);
        out.writeLong(7);
        out.writeByte(END);
    }

    @Test
    void tagsAroundTheSectionsAreSkipped() throws IOException {
        List<String> firstIds = new ArrayList<>(Collections.nCopies(64, "cbc:a"));
        firstIds.set(5, "cbc:b");
        firstIds.set(63, "minecraft:plains");
        List<String> palette = new ArrayList<>(List.of("cbc:a", "cbc:b", "minecraft:plains"));
        long[] data = RegionFixtures.pack(layoutOf(firstIds, palette), 3);
        byte[] nbt = RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("a name the reader steps over");
            everyKindOfTag(out, "before");
            tag(out, LIST, "block_ticks");
            out.writeByte(COMPOUND);
            out.writeInt(1);
            tag(out, STRING, "i");
            out.writeUTF("minecraft:water");
            tag(out, INT, "x");
            out.writeInt(1);
            out.writeByte(END);
            tag(out, INT, "DataVersion");
            out.writeInt(4903);
            tag(out, LIST, "sections");
            out.writeByte(COMPOUND);
            out.writeInt(2);
            // the first section keeps its long-array block_states in front of the biomes
            tag(out, BYTE, "Y");
            out.writeByte(-4);
            tag(out, COMPOUND, "block_states");
            tag(out, LONG_ARRAY, "data");
            out.writeInt(256);
            for (int i = 0; i < 256; i++) {
                out.writeLong(0x0123456789ABCDEFL);
            }
            out.writeByte(END);
            biomesWith(out, palette, data);
            out.writeByte(END);
            // the second keeps them behind, and has a tag of its own in between
            tag(out, BYTE, "Y");
            out.writeByte(7);
            biomes(out, Collections.nCopies(64, "cbc:c"));
            tag(out, COMPOUND, "block_states");
            tag(out, LONG_ARRAY, "data");
            out.writeInt(256);
            for (int i = 0; i < 256; i++) {
                out.writeLong(-1L);
            }
            out.writeByte(END);
            everyKindOfTag(out, "inside");
            out.writeByte(END);
            tag(out, STRING, "Status");
            out.writeUTF("minecraft:full");
            tag(out, COMPOUND, "Heightmaps");
            tag(out, LONG_ARRAY, "WORLD_SURFACE");
            out.writeInt(37);
            for (int i = 0; i < 37; i++) {
                out.writeLong(-1L);
            }
            out.writeByte(END);
            tag(out, LIST, "block_entities");
            out.writeByte(COMPOUND);
            out.writeInt(1);
            tag(out, STRING, "id");
            out.writeUTF("minecraft:chest");
            out.writeByte(END);
            everyKindOfTag(out, "after");
            out.writeByte(END);
        });

        ChunkNbt.Chunk chunk = ChunkNbt.parse(nbt);

        assertEquals(4903, chunk.dataVersion());
        assertEquals("minecraft:full", chunk.status());
        assertEquals(2, chunk.sections().size());
        ChunkNbt.Section first = chunk.sections().get(0);
        assertEquals(-4, first.y());
        assertEquals(palette, first.palette());
        assertArrayEquals(data, first.data());
        ChunkNbt.Section second = chunk.sections().get(1);
        assertEquals(7, second.y());
        assertEquals(List.of("cbc:c"), second.palette());
        assertNull(second.data());
    }

    /** The palette index of each id, which is what the game packs. */
    private static int[] layoutOf(List<String> ids, List<String> palette) {
        return ids.stream().mapToInt(palette::indexOf).toArray();
    }

    @Test
    void aChunkOfRealisticSectionsGivesTheirYAndBiomes() throws IOException {
        List<String> mixed = new ArrayList<>(Collections.nCopies(64, "cbc:a"));
        mixed.set(63, "mcme:custom_003");
        byte[] nbt = RegionFixtures.chunk(3337, "minecraft:full",
                Map.of(-4, Collections.nCopies(64, "minecraft:plains"), 46, mixed));

        ChunkNbt.Chunk chunk = ChunkNbt.parse(nbt);

        assertEquals(3337, chunk.dataVersion());
        assertEquals("minecraft:full", chunk.status());
        assertEquals(2, chunk.sections().size());
        assertEquals(-4, chunk.sections().get(0).y());
        assertEquals(List.of("minecraft:plains"), chunk.sections().get(0).palette());
        assertEquals(46, chunk.sections().get(1).y());
        assertEquals(List.of("cbc:a", "mcme:custom_003"), chunk.sections().get(1).palette());
        assertArrayEquals(layoutOf(mixed, chunk.sections().get(1).palette()),
                ChunkNbt.cells(chunk.sections().get(1).palette(), chunk.sections().get(1).data()));
    }

    @Test
    void aSectionWithoutBiomesIsLeftOut() throws IOException {
        // Real chunks have a section like this above and below the world, for the light engine
        byte[] nbt = RegionFixtures.chunkOf(4903, "minecraft:full", List.of(
                out -> {
                    tag(out, BYTE, "Y");
                    out.writeByte(-5);
                    tag(out, INT, "starlight.skylight_state");
                    out.writeInt(1);
                },
                out -> {
                    tag(out, BYTE, "Y");
                    out.writeByte(0);
                    biomesWith(out, List.of(), null);
                },
                out -> {
                    tag(out, BYTE, "Y");
                    out.writeByte(1);
                    biomes(out, Collections.nCopies(64, "cbc:a"));
                },
                out -> biomes(out, Collections.nCopies(64, "cbc:b")))); // no Y

        ChunkNbt.Chunk chunk = ChunkNbt.parse(nbt);

        assertEquals(1, chunk.sections().size());
        assertEquals(1, chunk.sections().get(0).y());
    }

    @Test
    void aTagWithTheRightNameAndTheWrongTypeIsNotTakenForTheOneWanted() throws IOException {
        byte[] nbt = RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            tag(out, STRING, "DataVersion");
            out.writeUTF("4903");
            tag(out, INT, "Status");
            out.writeInt(1);
            tag(out, COMPOUND, "sections");
            tag(out, BYTE, "Y");
            out.writeByte(3);
            out.writeByte(END);
            out.writeByte(END);
        });

        ChunkNbt.Chunk chunk = ChunkNbt.parse(nbt);

        assertNull(chunk.dataVersion());
        assertNull(chunk.status());
        assertEquals(List.of(), chunk.sections());
    }

    @Test
    void aSectionsListOfAnotherKindIsSkipped() throws IOException {
        byte[] nbt = RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            tag(out, LIST, "sections");
            out.writeByte(STRING);
            out.writeInt(2);
            out.writeUTF("a");
            out.writeUTF("b");
            tag(out, INT, "DataVersion");
            out.writeInt(4903);
            out.writeByte(END);
        });

        ChunkNbt.Chunk chunk = ChunkNbt.parse(nbt);

        assertEquals(List.of(), chunk.sections());
        assertEquals(4903, chunk.dataVersion(), "the tag after the list is still found");
    }

    @Test
    void aChunkWithoutSectionsOrVersionStillParses() throws IOException {
        ChunkNbt.Chunk chunk = ChunkNbt.parse(RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            out.writeByte(END);
        }));

        assertNull(chunk.dataVersion());
        assertNull(chunk.status());
        assertTrue(chunk.sections().isEmpty());
    }

    @Test
    void anOldLevelLayoutIsRefused() {
        IOException refused = assertThrows(IOException.class, () -> ChunkNbt.parse(RegionFixtures.levelChunk()));

        assertEquals("old chunk layout (Level)", refused.getMessage());
    }

    @Test
    void aRootThatIsNotACompoundIsRefused() {
        assertThrows(IOException.class, () -> ChunkNbt.parse(RegionFixtures.nbt(out -> {
            out.writeByte(LIST);
            out.writeUTF("");
        })));
    }

    // Damage must come out as an IOException the reader can count, never as an out-of-bounds error or a huge array.
    @Test
    void aChunkThatEndsEarlyIsRefusedWhereverItIsCut() {
        byte[] nbt = RegionFixtures.chunk(4903, "minecraft:full", Map.of(0, Collections.nCopies(64, "cbc:a")));

        for (int length = 0; length < nbt.length; length++) {
            byte[] cut = Arrays.copyOf(nbt, length);
            assertThrows(IOException.class, () -> ChunkNbt.parse(cut), "cut after " + length + " bytes");
        }
    }

    @Test
    void anArrayOrAStringLongerThanTheDataIsRefused() {
        for (int type : new int[] {BYTE_ARRAY, INT_ARRAY, LONG_ARRAY}) {
            for (int claimed : new int[] {-1, 1_000_000, Integer.MAX_VALUE}) {
                byte[] nbt = RegionFixtures.nbt(out -> {
                    out.writeByte(COMPOUND);
                    out.writeUTF("");
                    tag(out, type, "x");
                    out.writeInt(claimed);
                    out.writeByte(END);
                });
                assertThrows(IOException.class, () -> ChunkNbt.parse(nbt), "type " + type + " claiming " + claimed);
            }
        }
        assertThrows(IOException.class, () -> ChunkNbt.parse(RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            tag(out, STRING, "x");
            out.writeShort(1000); // a string's length is two bytes
            out.writeByte(END);
        })));
    }

    @Test
    void aListLongerThanTheDataIsRefusedAndOneOfNegativeLengthIsEmpty() {
        for (int kind : new int[] {COMPOUND, STRING, INT, END}) {
            for (int claimed : new int[] {1_000_000, Integer.MAX_VALUE}) {
                byte[] nbt = RegionFixtures.nbt(out -> {
                    out.writeByte(COMPOUND);
                    out.writeUTF("");
                    tag(out, LIST, "x");
                    out.writeByte(kind);
                    out.writeInt(claimed);
                    out.writeByte(END);
                });
                assertThrows(IOException.class, () -> ChunkNbt.parse(nbt), "list of " + kind + " claiming " + claimed);
            }
        }
        // the game reads a negative length as no elements
        assertDoesNotThrow(() -> ChunkNbt.parse(RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            tag(out, LIST, "x");
            out.writeByte(INT);
            out.writeInt(-1);
            out.writeByte(END);
        })));
    }

    @Test
    void anUnknownTagTypeIsRefused() {
        assertThrows(IOException.class, () -> ChunkNbt.parse(RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            tag(out, 13, "x");
            out.writeByte(END);
        })));
    }

    /** A chunk with a compound inside a compound inside a compound, {@code depth} deep. */
    private static byte[] nestedChunk(int depth) {
        return RegionFixtures.nbt(out -> {
            out.writeByte(COMPOUND);
            out.writeUTF("");
            for (int i = 0; i < depth; i++) {
                tag(out, COMPOUND, "n");
            }
            for (int i = 0; i < depth; i++) {
                out.writeByte(END);
            }
            out.writeByte(END);
        });
    }

    // The game reads at most 512 levels, so nothing it saved goes deeper; more would overflow the stack.
    @Test
    void nestingTheGameAllowsIsSkippedAndDeeperIsRefused() {
        assertDoesNotThrow(() -> ChunkNbt.parse(nestedChunk(100)));
        assertThrows(IOException.class, () -> ChunkNbt.parse(nestedChunk(600)));
        assertThrows(IOException.class, () -> ChunkNbt.parse(nestedChunk(100_000)));
    }
}
