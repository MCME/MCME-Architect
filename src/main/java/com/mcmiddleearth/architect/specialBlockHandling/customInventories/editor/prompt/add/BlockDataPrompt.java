package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class BlockDataPrompt extends ValidatingPrompt implements Listener {
    private boolean listenerRegistered = false;

    private ConversationContext conversationContext;

    private final String[] blockStateKeys;

    public BlockDataPrompt(String... blockStateKeys) {
        this.blockStateKeys = blockStateKeys;
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Make sure you hold in main hand the item you want to add to custom inventory. " +
                "Then left-click a block to use for blockData"+blockStateKeys[0]+".";
    }

    @Override
    public boolean blocksForInput(@NotNull ConversationContext conversationContext) {
        if(!listenerRegistered) {
            this.conversationContext = conversationContext;
            Bukkit.getPluginManager().registerEvents(this, Objects.requireNonNull(conversationContext.getPlugin()));
            listenerRegistered = true;
        }
Logger.getGlobal().info("blocksForInput: "+true);
        return true;
    }

    @Override
    protected boolean isInputValid(@NotNull ConversationContext conversationContext, @NotNull String input) {
        return input.equals("__d0nE__");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(blockStateKeys.length>1) {
            return new BlockDataPrompt(Arrays.copyOfRange(blockStateKeys,1,blockStateKeys.length));
        } else {
            return new ItemPrompt();
        }
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onSelectBlockstate(PlayerInteractEvent event) {
Logger.getGlobal().info("onSelectBlockstate");
        if(event.getAction().equals(Action.LEFT_CLICK_BLOCK)
                && event.hasItem()) {
            Block block = event.getClickedBlock();
Logger.getGlobal().info("Block: "+block);
            if(block != null) {
Logger.getGlobal().info("BlockData: "+block.getBlockData());
                Map<String,String> blockDatas = (Map<String, String>) Objects.requireNonNull(conversationContext.getSessionData("blockData"));
                blockDatas.put(blockStateKeys[0], block.getBlockData().getAsString());
                HandlerList.unregisterAll(this);
                event.setCancelled(true);
                conversationContext.getForWhom().acceptConversationInput("__d0nE__");
            }
        }
    }
}
