package com.mcmiddleearth.architect.specialBlockHandling.itemBlock;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.world.NullWorld;
import com.sk89q.worldedit.world.World;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

// The item-block region the web map reads a block column's limit from. One mock/load per class, as
// ItemBlockManager keeps Architect's data folder in a static field.
class ItemBlockRegionTest {

    private static final String WORLD = NullWorld.getInstance().getName();

    @BeforeAll
    static void setUp() {
        MockBukkit.mock();
        MockBukkit.load(ArchitectPlugin.class);
    }

    @AfterAll
    static void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemBlockRegion region(String name, World world, int limit) {
        ItemBlockRegion region = new ItemBlockRegion(name, new CuboidRegion(world, BlockVector3.at(0, 0, 0),
                BlockVector3.at(31, 255, 31)));
        region.setLimit(limit);
        return region;
    }

    // /itemblock create hands over the admin's live WorldEdit selection, which //pos1 and //pos2 go on reshaping.
    @Test
    void aRegionKeepsItsOwnCopyOfTheSelectionItWasMadeFrom() {
        CuboidRegion selection = new CuboidRegion(NullWorld.getInstance(), BlockVector3.at(0, 0, 0),
                BlockVector3.at(31, 255, 31));
        ItemBlockRegion region = new ItemBlockRegion("Pier", selection);

        selection.setPos1(BlockVector3.at(500, 0, 500)); // the admin's next //pos1

        assertEquals(BlockVector3.at(0, 0, 0), region.getRegion().getMinimumPoint(), "the region stays put");
        assertTrue(region.coversColumn(WORLD, 8, 8));
    }

    @Test
    void aNewShapeIsCopiedToo() {
        ItemBlockRegion region = region("Pier", NullWorld.getInstance(), 3);
        CuboidRegion selection = new CuboidRegion(NullWorld.getInstance(), BlockVector3.at(64, 0, 64),
                BlockVector3.at(95, 255, 95));

        region.setRegion(selection);
        selection.setPos1(BlockVector3.at(0, 0, 0));

        assertEquals(BlockVector3.at(64, 0, 64), region.getRegion().getMinimumPoint());
    }

    // A stale region must not fail every lookup: the budget layer would keep its old tiles and stop saving.
    @Test
    void aRegionWhoseWorldIsGoneCoversNoColumnAndTheOthersStillCount() {
        NullWorld unloaded = new NullWorld() {
            @Override
            public String getName() { // as WorldEdit's BukkitWorld does once its world is unloaded
                throw new NullPointerException("The world was unloaded and the reference is unavailable");
            }
        };
        ItemBlockRegion gone = region("Gone", unloaded, 9);

        assertFalse(gone.coversColumn(WORLD, 8, 8), "its world was unloaded");
        assertFalse(region("Nowhere", null, 9).coversColumn(WORLD, 8, 8), "no world at all");
        ItemBlockManager.addRegion(gone);
        ItemBlockManager.addRegion(region("Harbour", NullWorld.getInstance(), 3));
        assertEquals("Harbour", ItemBlockManager.regionForColumn(WORLD, 8, 8).getName(),
                "the stale region is skipped, though its limit is higher");

        NullWorld other = new NullWorld() {
            @Override
            public String getName() {
                return "other";
            }
        };
        ItemBlockManager.addRegion(region("Quay", NullWorld.getInstance(), 7));
        ItemBlockManager.addRegion(region("Beacon", NullWorld.getInstance(), 5));
        ItemBlockManager.addRegion(region("Elsewhere", other, 11));
        assertEquals("Quay", ItemBlockManager.regionForColumn(WORLD, 8, 8).getName(),
                "as for placing, the highest limit over the column wins; a region in another world does not count");
    }

    // The map reads a column's limit whatever the height: an ellipsoid counts where it is widest.
    @Test
    void anEllipsoidCoversTheColumnsUnderItsWidestPart() {
        ItemBlockRegion dome = new ItemBlockRegion("Dome", new EllipsoidRegion(NullWorld.getInstance(),
                BlockVector3.at(0, 64, 0), Vector3.at(10, 5, 10)));

        assertTrue(dome.coversColumn(WORLD, 10, 0), "its rim, at its middle height");
        assertFalse(dome.coversColumn(WORLD, 11, 0), "just outside it");
    }
}
