package com.mcmiddleearth.architect.specialBlockHandling.itemBlock;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;

/**
 * The entities that count toward a chunk's item-block limit: armor stands (item blocks are armor stands), item frames
 * (glow item frames too) and paintings. Placing and the web map both count with this rule.
 */
public enum ItemBlockCount {
    ARMOR_STAND, ITEM_FRAME, PAINTING;

    /** The kind of entity, or null when it does not count. */
    public static ItemBlockCount of(Entity entity) {
        if (entity instanceof ArmorStand) {
            return ARMOR_STAND;
        }
        if (entity instanceof ItemFrame) {
            return ITEM_FRAME;
        }
        return entity instanceof Painting ? PAINTING : null;
    }

    public static boolean countsTowardLimit(Entity entity) {
        return of(entity) != null;
    }
}
