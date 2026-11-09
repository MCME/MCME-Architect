package com.mcmiddleearth.architect.slabSmooth;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PackSources for the wiring and command tests: one pack, Human, small enough for the real Packs to build in moments.
 * Its zip holds stone; the client jar holds a slab of stone (a comparator, as MCME's packs use), bricks and a slab of
 * bricks (a purpur slab). In its inventory stone is terrain and bricks are a building block, so bricks are smoothed
 * only with blocks all. No RP region and no player has a pack, so the normal choice is always the default, Human.
 * It records what it was asked about, and on which thread.
 */
final class FakePackSources implements PackSources {

    static final String PACK = "Human";

    /** The client jar; Architect's config must name it as slabSmooth.vanilla.clientJar, with slabSmooth.download false. */
    final Path clientJar;
    private final Path inventory;
    /** "inventory Human on <thread>" and "source Human on <thread>", in order. */
    final List<String> asked = Collections.synchronizedList(new ArrayList<>());
    /** While set, asking for the inventory waits for it: a check that has begun and can't get on. */
    volatile CountDownLatch gate;
    /** Counted down when something waits at the gate. */
    volatile CountDownLatch entered;

    /**
     * Writes the pack's zip into dataFolder/slabSmooth/packs/, where Packs looks before it would download one, and the
     * client jar and the inventory into dir.
     */
    FakePackSources(Path dir, Path dataFolder) throws IOException {
        Path zip = dataFolder.resolve("slabSmooth").resolve("packs").resolve(PACK + ".zip");
        clientJar = dir.resolve("client.jar");
        inventory = dir.resolve("inventory");
        Files.createDirectories(zip.getParent());
        Files.createDirectories(inventory);
        writeZip(zip, Map.of(
                "pack.mcmeta", "{}",
                "assets/minecraft/blockstates/stone.json", "{\"variants\": {\"\": {\"model\": \"block/rock\"}}}",
                "assets/minecraft/models/block/rock.json", model("block/rock", 16)));
        writeZip(clientJar, Map.of(
                "assets/minecraft/blockstates/comparator.json", "{\"variants\": {\"facing=north\": {\"model\": \"block/rock_slab\"}}}",
                "assets/minecraft/models/block/rock_slab.json", model("block/rock", 8),
                "assets/minecraft/blockstates/bricks.json", "{\"variants\": {\"\": {\"model\": \"block/brick\"}}}",
                "assets/minecraft/models/block/brick.json", model("block/brick", 16),
                "assets/minecraft/blockstates/purpur_slab.json", "{\"variants\": {\"type=bottom\": {\"model\": \"block/brick_slab\"}}}",
                "assets/minecraft/models/block/brick_slab.json", model("block/brick", 8)));
        Files.writeString(inventory.resolve("terra.yml"), """
                Items:
                  rock:
                    type: VANILLA
                    itemMaterial: stone
                    category: Terra
                    display: Rock
                  rock_slab:
                    type: BLOCK
                    blockData: "minecraft:comparator[facing=north]"
                    display: Rock slab
                    collection:
                      indirect: rock
                """);
        Files.writeString(inventory.resolve("building.yml"), """
                Items:
                  bricks:
                    type: VANILLA
                    itemMaterial: bricks
                    category: Building
                    display: Bricks
                  brick_slab:
                    type: BLOCK
                    blockData: "minecraft:purpur_slab[type=bottom]"
                    display: Brick slab
                    collection:
                      indirect: bricks
                """);
    }

    private static String model(String texture, int top) {
        return """
                {"textures": {"t": "%s"}, "elements": [{"from": [0, 0, 0], "to": [16, %d, 16], "faces": {
                  "up": {"texture": "#t"}, "down": {"texture": "#t"}, "north": {"texture": "#t"},
                  "south": {"texture": "#t"}, "east": {"texture": "#t"}, "west": {"texture": "#t"}}}]}""".formatted(texture, top);
    }

    private static void writeZip(Path file, Map<String, String> entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : new LinkedHashMap<>(entries).entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    @Override
    public List<String> packs() {
        return List.of(PACK);
    }

    @Override
    public Path inventory(String pack) {
        asked.add("inventory " + pack + " on " + Thread.currentThread().getName());
        CountDownLatch g = gate;
        if (g != null) {
            CountDownLatch e = entered;
            if (e != null) {
                e.countDown();
            }
            try {
                g.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        return inventory;
    }

    @Override
    public Source source(String pack) {
        asked.add("source " + pack + " on " + Thread.currentThread().getName());
        return null; // the zip is in the data folder
    }

    @Override
    public String regionPack(Location location) {
        return null;
    }

    @Override
    public Set<String> regionPacksIn(World world, BlockVector3 min, BlockVector3 max) {
        return Set.of();
    }

    @Override
    public String currentPack(Player player) {
        return null;
    }
}
