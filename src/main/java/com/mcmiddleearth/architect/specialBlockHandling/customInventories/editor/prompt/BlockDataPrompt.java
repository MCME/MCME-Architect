package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class BlockDataPrompt implements Prompt, Listener {

    private boolean done = false;

    private boolean listenerRegistered = false;

    private ConversationContext conversationContext;

    private final String[] blockStateKeys;

    public BlockDataPrompt(String... blockStateKeys) {
        this.blockStateKeys = blockStateKeys;
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Left-click a block for blockData"+blockStateKeys[0]+".";
    }

    @Override
    public boolean blocksForInput(@NotNull ConversationContext conversationContext) {
        if(!listenerRegistered) {
            this.conversationContext = conversationContext;
            Bukkit.getPluginManager().registerEvents(this, Objects.requireNonNull(conversationContext.getPlugin()));
            listenerRegistered = true;
        }
        return !done;
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext conversationContext, @Nullable String input) {
        if(blockStateKeys.length>1) {
            return new BlockDataPrompt(Arrays.copyOfRange(blockStateKeys,1,blockStateKeys.length));
        } else {
            return new DisplayPrompt();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSelectBlockstate(PlayerInteractEvent event) {
        if(event.getAction().equals(Action.LEFT_CLICK_BLOCK)
                && event.hasItem()) {
            Block block = event.getClickedBlock();
            if(block != null) {
                ((Map<String, String>) Objects.requireNonNull(conversationContext.getSessionData("blockData")))
                        .put(blockStateKeys[0], block.getBlockData().getAsString());
                conversationContext.setSessionData("inventoryItem", event.getItem());
                done = true;
            }
        }
    }
}
