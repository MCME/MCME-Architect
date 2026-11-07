package com.mcmiddleearth.architect.slabSmooth.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The table builder's rules on a tiny made-up pack and inventory (needs no files). */
class TableBuilderRulesTest {

    private static final Map<String, String> FILES = new HashMap<>();

    private static void blockstate(String block, String json) {
        FILES.put("assets/minecraft/blockstates/" + block + ".json", json);
    }

    private static void model(String name, String texture, int[] from, int[] to) {
        FILES.put("assets/minecraft/models/block/" + name + ".json", """
                {"textures": {"t": "%s"}, "elements": [{"from": [%d, %d, %d], "to": [%d, %d, %d], "faces": {
                  "up": {"texture": "#t"}, "down": {"texture": "#t"}, "north": {"texture": "#t"},
                  "south": {"texture": "#t"}, "east": {"texture": "#t"}, "west": {"texture": "#t"}}}]}"""
                .formatted(texture, from[0], from[1], from[2], to[0], to[1], to[2]));
    }

    static {
        model("rock", "block/rock", new int[] {0, 0, 0}, new int[] {16, 16, 16});
        model("rock_slab", "block/rock", new int[] {0, 0, 0}, new int[] {16, 8, 16});
        model("rock_thin", "block/rock", new int[] {0, 0, 0}, new int[] {16, 4, 16});
        model("rock_north_half", "block/rock", new int[] {0, 0, 0}, new int[] {16, 16, 8});
        model("sand", "block/sand", new int[] {0, 0, 0}, new int[] {16, 16, 16});
        model("brick", "block/brick", new int[] {0, 0, 0}, new int[] {16, 16, 16});
        model("brick_slab", "block/brick", new int[] {0, 0, 0}, new int[] {16, 8, 16});
        model("rock_slab_b", "block/rock", new int[] {0, 0, 0}, new int[] {16, 8, 16});
        blockstate("stone", "{\"variants\": {\"\": {\"model\": \"block/rock\"}}}");
        blockstate("comparator", "{\"variants\": {\"facing=north\": {\"model\": \"block/rock_slab\"}}}");
        blockstate("repeater", "{\"variants\": {\"facing=north\": {\"model\": \"block/rock_thin\"}}}");
        blockstate("purpur_slab", "{\"variants\": {\"type=bottom\": {\"model\": \"block/rock_slab\"}}}");
        blockstate("oak_fence_gate", """
                {"variants": {"facing=north": {"model": "block/rock_north_half"},
                  "facing=east": {"model": "block/rock_north_half", "y": 90},
                  "facing=south": {"model": "block/rock_north_half", "y": 180},
                  "facing=west": {"model": "block/rock_north_half", "y": 270}}}""");
        blockstate("sand", "{\"variants\": {\"\": {\"model\": \"block/sand\"}}}");
        blockstate("bricks", "{\"variants\": {\"\": {\"model\": \"block/brick\"}}}");
        blockstate("brick_slab", "{\"variants\": {\"type=bottom\": {\"model\": \"block/brick_slab\"}}}");
        blockstate("smooth_stone_slab", "{\"variants\": {\"type=bottom\": {\"model\": \"block/rock_slab_b\"}}}");
        // used only by the tests that add these blocks to the inventory
        model("barrel", "block/barrel", new int[] {0, 0, 0}, new int[] {16, 16, 16});
        model("barrel_slab", "block/barrel", new int[] {0, 0, 0}, new int[] {16, 8, 16});
        blockstate("barrel", "{\"variants\": {\"facing=up,open=false\": {\"model\": \"block/barrel\"}}}");
        blockstate("spruce_slab", "{\"variants\": {\"type=bottom\": {\"model\": \"block/barrel_slab\"}}}");
        FILES.put("assets/minecraft/models/block/mud.json", "{\"elements\": [{\"from\": [0, 0], \"to\": [16, 16, 16]}]}");
        blockstate("mud", "{\"variants\": {\"\": {\"model\": \"block/mud\"}}}");
    }

    private static final Resources PACK = path -> {
        String s = FILES.get(path);
        return s == null ? null : s.getBytes(StandardCharsets.UTF_8);
    };

    private static Inventory.Entry entry(String key, String category, String type, Map<String, String> states, String parent) {
        return new Inventory.Entry(key, "terra.yml", category, type, key, states, parent);
    }

    private static Table build() {
        return new Table(spec(new TableBuilder.Settings()));
    }

    private static com.google.gson.JsonObject spec(TableBuilder.Settings settings) {
        return result(settings, List.of()).table();
    }

    /** The table built from the inventory below, plus extra entries. */
    private static TableBuilder.Result result(TableBuilder.Settings settings, List<Inventory.Entry> extra) {
        List<Inventory.Entry> entries = new ArrayList<>(List.of(
                entry("rock", "Terra", "VANILLA", Map.of("block", "minecraft:stone"), ""),
                // shown in rock's collection, so no category of its own
                entry("rock_slab", "", "BLOCK", Map.of("blockData", "minecraft:comparator[facing=north]"), "rock"),
                entry("rock_thin_slab", "Terra", "BLOCK", Map.of("blockData", "minecraft:repeater[facing=north]"), ""),
                entry("rock_wet_slab", "Terra", "BLOCK", Map.of("blockData", "minecraft:purpur_slab[type=bottom,waterlogged=true]"), ""),
                entry("rock_vertical_slab", "Terra", "FOUR_DIRECTIONS", Map.of(
                        "blockDataNorth", "minecraft:oak_fence_gate[facing=north]", "blockDataEast", "minecraft:oak_fence_gate[facing=east]",
                        "blockDataSouth", "minecraft:oak_fence_gate[facing=south]", "blockDataWest", "minecraft:oak_fence_gate[facing=west]"), ""),
                entry("sand", "Terra", "BLOCK", Map.of("blockData", "minecraft:sand"), ""),
                entry("flower", "Plant", "BLOCK", Map.of("blockData", "minecraft:poppy"), ""),
                // a building block with a slab of its own, and a building slab with the rock texture
                entry("bricks", "Block", "VANILLA", Map.of("block", "minecraft:bricks"), ""),
                entry("brick_slab", "", "VANILLA", Map.of("block", "minecraft:brick_slab"), "bricks"),
                entry("smooth_rock_slab", "Block", "VANILLA", Map.of("block", "minecraft:smooth_stone_slab"), "")));
        entries.addAll(extra);
        return TableBuilder.build("Test", Inventory.inheritCategories(entries), new Models(PACK), settings);
    }

    @Test
    void piecesFollowTheirModelsAndTextures() {
        Table table = build();
        Table.Kind3 stone = table.kind3("minecraft:stone");
        assertEquals(Table.TERRAIN, stone.category());
        Table.Material rock = table.materials.get(stone.material());
        assertEquals("minecraft:comparator[facing=north]", rock.shapes.get(Octants.SLAB), "slab from the collection");
        // Turned models: each fence gate state is filed under the side it really fills.
        assertEquals("minecraft:oak_fence_gate[facing=north]", rock.shapes.get(Octants.column(Shapes.NORTH)));
        assertEquals("minecraft:oak_fence_gate[facing=east]", rock.shapes.get(Octants.column(Shapes.EAST)));
        assertEquals("minecraft:oak_fence_gate[facing=south]", rock.shapes.get(Octants.column(Shapes.SOUTH)));
        assertEquals("minecraft:oak_fence_gate[facing=west]", rock.shapes.get(Octants.column(Shapes.WEST)));
        assertEquals(5, rock.shapes.size(), rock.shapes.toString());
    }

    @Test
    void thinWaterloggedAndUnmatchedBlocksStayOut() {
        Table table = build();
        assertEquals(Table.OTHER, table.kind3("minecraft:repeater[facing=north]").category(), "a 4-pixel slab isn't a half slab");
        assertEquals(Table.OTHER, table.kind3("minecraft:purpur_slab[type=bottom,waterlogged=true]").category(), "would place water");
        assertEquals(Table.OTHER, table.kind3("minecraft:sand").category(), "no sand pieces, so sand isn't touched");
        assertEquals(Table.DECOR, table.kind3("minecraft:poppy").category());
        assertNull(new Models(PACK).cleanMask("minecraft:repeater[facing=north]"));
        assertTrue(table.materials.size() == 1);
    }

    @Test
    void otherCategoriesOnlyWithBlocksAll() {
        com.google.gson.JsonObject spec = spec(new TableBuilder.Settings());
        Table terrain = new Table(spec), all = new Table(spec, true);
        assertEquals(Table.OTHER, terrain.kind3("minecraft:bricks").category(), "bricks are a building block");
        Table.Kind3 bricks = all.kind3("minecraft:bricks");
        assertEquals(Table.TERRAIN, bricks.category(), "but have a slab of their own");
        assertEquals("minecraft:brick_slab[type=bottom,waterlogged=false]", all.materials.get(bricks.material()).shapes.get(Octants.SLAB));
        assertEquals("bricks", all.materials.get(bricks.material()).label);
        // Terrain doesn't take pieces from building categories, even with its own texture.
        Table.Material rock = terrain.materials.get(terrain.kind3("minecraft:stone").material());
        assertEquals("minecraft:comparator[facing=north]", rock.shapes.get(Octants.SLAB));
        assertEquals(1, terrain.materials.size());
    }

    @Test
    void configCanMakeABlockTerrain() {
        TableBuilder.Settings settings = new TableBuilder.Settings();
        settings.terrain = java.util.Set.of("bricks");
        Table terrain = new Table(spec(settings));
        assertEquals(Table.TERRAIN, terrain.kind3("minecraft:bricks").category());
        assertEquals(2, terrain.materials.size());
    }

    @Test
    void namesPairPiecesWithTheirBlock() {
        assertEquals("dirt", TableBuilder.baseName("Dirt Slab (lower)"));
        assertEquals("dirt", TableBuilder.baseName("Dirt Block"));
        assertEquals("continental sand", TableBuilder.baseName("Continental Sand Slab"));
        assertEquals("stone", TableBuilder.baseName("Stone Vertical Slab"));
    }

    private static final String BARREL = "minecraft:barrel[facing=up,open=false]";

    /** Cutting a barrel would empty it, so it isn't smoothed even with blocks all, though it has a slab of its own. */
    @Test
    void aBlockEntityFullBlockNeverBecomesAMaterial() {
        TableBuilder.Settings settings = new TableBuilder.Settings();
        settings.hasBlockEntity = BARREL::equals;
        com.google.gson.JsonObject spec = result(settings, List.of(
                new Inventory.Entry("barrel", "block.yml", "Block", "BLOCK", "Barrel", Map.of("blockData", BARREL), ""),
                new Inventory.Entry("barrel_slab", "block.yml", "Block", "BLOCK", "Barrel Slab",
                        Map.of("blockData", "minecraft:spruce_slab[type=bottom,waterlogged=false]"), ""))).table();
        assertEquals(List.of(), materialsWith(spec, BARREL));
        assertEquals(Table.OTHER, new Table(spec, true).kind3(BARREL).category());
    }

    /** MCME's packs use comparators, which are block entities, as slabs on purpose. */
    @Test
    void aBlockEntityPieceStaysAPiece() {
        TableBuilder.Settings settings = new TableBuilder.Settings();
        settings.hasBlockEntity = state -> state.startsWith("minecraft:comparator");
        Table table = new Table(spec(settings));
        Table.Material rock = table.materials.get(table.kind3("minecraft:stone").material());
        assertEquals("minecraft:comparator[facing=north]", rock.shapes.get(Octants.SLAB));
    }

    @Test
    void aStateTheServerDoesNotKnowIsDropped() {
        String unknown = "minecraft:oak_fence_gate[facing=east]";
        TableBuilder.Settings settings = new TableBuilder.Settings();
        settings.validState = state -> !state.equals(unknown);
        TableBuilder.Result result = result(settings, List.of());
        assertEquals(List.of(), materialsWith(result.table(), unknown));
        Table table = new Table(result.table());
        Table.Material rock = table.materials.get(table.kind3("minecraft:stone").material());
        assertEquals("minecraft:oak_fence_gate[facing=north]", rock.shapes.get(Octants.column(Shapes.NORTH)), "its entry's other states stay");
        assertTrue(result.report().stream().anyMatch(line -> line.contains(unknown)), String.join("\n", result.report()));
    }

    @Test
    void aBrokenModelSkipsOnlyThatBlock() {
        TableBuilder.Result result = assertDoesNotThrow(() -> result(new TableBuilder.Settings(),
                List.of(entry("mud", "Terra", "BLOCK", Map.of("blockData", "minecraft:mud"), ""))));
        assertEquals(spec(new TableBuilder.Settings()).getAsJsonObject("materials").keySet(),
                result.table().getAsJsonObject("materials").keySet(), "the other materials are built");
        assertTrue(result.report().stream().anyMatch(line -> line.contains("minecraft:mud")), String.join("\n", result.report()));
    }

    /** Names of the materials that have the state as a full block or a piece. */
    private static List<String> materialsWith(com.google.gson.JsonObject spec, String state) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, com.google.gson.JsonElement> m : spec.getAsJsonObject("materials").entrySet()) {
            com.google.gson.JsonObject material = m.getValue().getAsJsonObject();
            List<String> states = new ArrayList<>();
            material.getAsJsonArray("full").forEach(s -> states.add(BlockStates.normalize(s.getAsString())));
            material.getAsJsonObject("shapes").entrySet().forEach(s -> states.add(BlockStates.normalize(s.getValue().getAsString())));
            if (states.contains(BlockStates.normalize(state))) {
                out.add(m.getKey());
            }
        }
        return out;
    }
}
