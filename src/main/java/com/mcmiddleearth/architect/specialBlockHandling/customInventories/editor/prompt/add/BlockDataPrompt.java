package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import com.mcmiddleearth.architect.ArchitectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.EquipmentSlot;
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
        return "Left-click a block to use for blockData"+blockStateKeys[0]+". You may also type in valid block data.";
    }

    @Override
    protected @Nullable String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
        return "You need to left-click a block or type in valid block data!";
    }

    @Override
    public boolean blocksForInput(@NotNull ConversationContext conversationContext) {
        if(!listenerRegistered) {
            this.conversationContext = conversationContext;
            Bukkit.getPluginManager().registerEvents(this, Objects.requireNonNull(conversationContext.getPlugin()));
            listenerRegistered = true;
        }
        return true;
    }

    @Override
    protected boolean isInputValid(@NotNull ConversationContext conversationContext, @NotNull String input) {
        try {
            Bukkit.createBlockData(input);
            return true;
        } catch(IllegalArgumentException ignore){}
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        HandlerList.unregisterAll(this);
        Map<String,String> blockDatas = (Map<String, String>) Objects.requireNonNull(conversationContext.getSessionData("blockData"));
        blockDatas.put(blockStateKeys[0], input);
        if(blockStateKeys.length>1) {
            return new BlockDataPrompt(Arrays.copyOfRange(blockStateKeys,1,blockStateKeys.length));
        } else {
            return new ItemPrompt();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSelectBlockstate(PlayerInteractEvent event) {
//Logger.getGlobal().info("onSelectBlockstate");
        if(event.getPlayer().equals(conversationContext.getForWhom()) && event.getAction().equals(Action.LEFT_CLICK_BLOCK)
                && event.getHand()!=null && event.getHand().equals(EquipmentSlot.HAND)
                && event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.PRISMARINE_SHARD)) {
            Block block = event.getClickedBlock();
//Logger.getGlobal().info("Block: "+block);
            if(block != null) {
//Logger.getGlobal().info("BlockData: "+block.getBlockData());
                HandlerList.unregisterAll(this);
                event.setCancelled(true);
                Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()-> {
                    conversationContext.getForWhom().acceptConversationInput(block.getBlockData().getAsString());
                }, 3);
            }
        }
    }

    protected String[] getBlockStateKeys() {
        return blockStateKeys;
    }
}
