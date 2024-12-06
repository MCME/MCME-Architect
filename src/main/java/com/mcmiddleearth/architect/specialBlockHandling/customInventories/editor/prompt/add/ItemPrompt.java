package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import org.bukkit.Material;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemPrompt extends ValidatingPrompt {

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        conversationContext.setSessionData("inventoryItem", ((Player)conversationContext.getForWhom()).getInventory().getItemInMainHand());
        return new CmdPrompt();
    }

    @Override
    protected boolean isInputValid(@NotNull ConversationContext context, @NotNull String input) {
        return !((Player)context.getForWhom()).getInventory().getItemInMainHand().getType().equals(Material.AIR)
              && input.equalsIgnoreCase("ok");

    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Hold the item you want to add to custom inventory in main hand and type 'ok'.";
    }
}
