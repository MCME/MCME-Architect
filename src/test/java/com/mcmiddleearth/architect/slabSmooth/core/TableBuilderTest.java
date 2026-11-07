package com.mcmiddleearth.architect.slabSmooth.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Builds tables from the packs on this machine (skipped where they aren't): the client's resourcepacks folder,
 * the RP-Inventories checkout and the 26.2 client jar. Override with -Dslabsmooth.packs, -Dslabsmooth.jar.
 */
class TableBuilderTest {

    static final Path PACKS = Path.of(System.getProperty("slabsmooth.packs",
            System.getenv().getOrDefault("APPDATA", "") + "/.minecraft/resourcepacks"));
    static final Path JAR = Path.of(System.getProperty("slabsmooth.jar",
            System.getenv().getOrDefault("APPDATA", "") + "/.minecraft/versions/26.2/26.2.jar"));

    static Models models(String pack) throws Exception {
        Path dir = PACKS.resolve(pack);
        assumeTrue(Files.isDirectory(dir.resolve("assets")) && Files.isRegularFile(JAR), "no local " + pack + " or client jar");
        return new Models(Resources.layered(List.of(Resources.dir(dir), Resources.zip(JAR))));
    }

    /** The port reads models like the prototype's probe: same octants for every state in octants.json. */
    @Test
    void masksMatchThePrototypesProbe() throws Exception {
        Models models = models("RP-Human");
        JsonObject probed;
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream("/slabSmooth/octants.json"), StandardCharsets.UTF_8)) {
            probed = JsonParser.parseReader(r).getAsJsonObject();
        }
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : probed.entrySet()) {
            Integer mask = models.mask(e.getKey());
            if (mask == null || mask != e.getValue().getAsInt()) {
                wrong.add(e.getKey() + ": " + mask + " vs " + e.getValue());
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
        assertTrue(probed.size() > 100);
    }

    /** Models can be turned: a fence gate vertical slab shows its big face on the side it fills. */
    @Test
    void facesFollowTheBlockstateRotation() throws Exception {
        Models models = models("RP-Human");
        for (String facing : List.of("north", "south", "east", "west")) {
            String state = "minecraft:crimson_fence_gate[facing=" + facing + ",in_wall=true,open=false,powered=true]";
            int mask = models.mask(state);
            int side = Directional.pieceMasks().get(mask);
            Models.Face out = models.face(state, side);
            assertTrue(out != null, state);
            assertEquals(models.face("minecraft:stone", side).texture(), out.texture(), state);
        }
    }

    static List<Inventory.Entry> humanInventory() throws Exception {
        Path inv = PACKS.resolve("RP-Inventories/inventories");
        assumeTrue(Files.isDirectory(inv), "no RP-Inventories checkout");
        return Inventory.read(inv, System.out::println);
    }

    /** Every piece of the hand-made RP-Human table is found again, with the same shape. */
    @Test
    void findsTheHandMadeHumanPieces() throws Exception {
        Models models = models("RP-Human");
        TableBuilder.Result built = TableBuilder.build("Human", humanInventory(), models, new TableBuilder.Settings());
        built.report().forEach(System.out::println);
        Table auto = new Table(built.table());
        Table hand = TestTables.handMade();
        List<String> missing = new ArrayList<>();
        for (Table.Material m : hand.materials) {
            for (Map.Entry<Integer, String> e : m.shapes.entrySet()) {
                Table.Kind3 k = auto.kind3(BlockStates.normalize(e.getValue()));
                if (k.category() != Table.PIECE || k.mask() != e.getKey()) {
                    missing.add(m.name + " " + e.getValue() + " -> " + k);
                }
            }
        }
        System.out.println("hand-made pieces not found: " + missing.size());
        missing.forEach(s -> System.out.println("  " + s));
        for (Table.Material m : hand.materials) {
            Table.Kind3 k = auto.kind3(m.fullState);
            Table.Material am = k.material() >= 0 ? auto.materials.get(k.material()) : null;
            System.out.println("hand " + m.name + " (" + m.fullState + ", " + m.shapes.size() + " pieces): "
                    + (am == null ? "NOT TERRAIN" : am.name + ", " + am.shapes.size() + " pieces, slab "
                    + (java.util.Objects.equals(am.shapes.get(Octants.SLAB), m.shapes.get(Octants.SLAB)) ? "same" : am.shapes.get(Octants.SLAB))));
        }
    }
}
