package com.mcmiddleearth.architect.mapLayers.customBiomes;

import com.mcmiddleearth.architect.mapLayers.customBiomes.SelectionSource.ChunkBox;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

// WorldEdit is optional, and WorldEditSelections is the one class that names its classes. MockBukkit runs no WorldEdit,
// so what is tested here is the lookup, the chunk arithmetic on a region, and how a WorldEdit that cannot answer is
// treated. A real session is checked on a server.
class WorldEditSelectionsTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void withoutWorldEditThereAreNoSelections() {
        assertSame(SelectionSource.NONE, WorldEditSelections.lookup(plugin));
    }

    @Test
    void noSelectionsGiveNoChunks() {
        server.addSimpleWorld("world");

        assertEquals(Optional.empty(), SelectionSource.NONE.chunks(server.addPlayer()));
    }

    @Test
    void anEnabledWorldEditGivesItsSelections() {
        MockBukkit.createMockPlugin("WorldEdit");

        assertInstanceOf(WorldEditSelections.class, WorldEditSelections.lookup(plugin));
    }

    @Test
    void anEnabledFastAsyncWorldEditGivesItsSelections() {
        MockBukkit.createMockPlugin("FastAsyncWorldEdit");

        assertInstanceOf(WorldEditSelections.class, WorldEditSelections.lookup(plugin));
    }

    @Test
    void aSelectionIsTheChunksOfItsLowestAndHighestBlocks() {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(-17, 0, 5), BlockVector3.at(33, 10, 40));

        assertEquals(new ChunkBox("world", -2, 0, 2, 2), WorldEditSelections.WorldEditCalls.boxOf("world", region));
    }

    @Test
    void theEdgesOfAChunkAreInThatChunk() {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(-16, 0, 15), BlockVector3.at(15, 10, 16));

        assertEquals(new ChunkBox("world", -1, 0, 0, 1), WorldEditSelections.WorldEditCalls.boxOf("world", region),
                "block -16 is the first of chunk -1, 15 the last of chunk 0, and 16 the first of chunk 1");
    }

    @Test
    void aSingleBlockIsItsChunk() {
        CuboidRegion region = new CuboidRegion(BlockVector3.at(100, 64, -100), BlockVector3.at(100, 64, -100));

        assertEquals(new ChunkBox("world", 6, -7, 6, -7), WorldEditSelections.WorldEditCalls.boxOf("world", region));
    }

    @Test
    void aWorldEditThatCannotAnswerGivesNoSelectionAndOneWarningPerStart() {
        server.addSimpleWorld("world");
        PlayerMock player = server.addPlayer();
        MockBukkit.createMockPlugin("WorldEdit"); // enabled, but no WorldEdit session exists to ask
        Warnings warnings = new Warnings();
        plugin.getLogger().addHandler(warnings);
        SelectionSource selections = WorldEditSelections.lookup(plugin);

        assertEquals(Optional.empty(), selections.chunks(player));
        assertEquals(Optional.empty(), selections.chunks(player));
        assertEquals(1, warnings.seen.size(), "one warning, not one for every command");

        SelectionSource restarted = WorldEditSelections.lookup(plugin);
        assertEquals(Optional.empty(), restarted.chunks(player));
        assertEquals(2, warnings.seen.size(), "and one more after another start");
    }

    private static final class Warnings extends Handler {
        private final List<LogRecord> seen = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                seen.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
