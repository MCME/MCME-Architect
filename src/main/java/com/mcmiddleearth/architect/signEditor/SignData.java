package com.mcmiddleearth.architect.signEditor;

import org.bukkit.block.Block;
import org.bukkit.block.sign.Side;

public class SignData {

    private final Block block;
    private final Side side;

    public SignData(Block block, Side side) {
        this.block = block;
        this.side = side;
    }

    public Block getBlock() {
        return block;
    }

    public Side getSide() {
        return side;
    }
}
