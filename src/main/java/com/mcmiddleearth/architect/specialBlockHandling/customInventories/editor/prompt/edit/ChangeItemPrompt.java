package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.DisplayPrompt;
import org.bukkit.Material;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeItemPrompt extends FixedSetPrompt {

    public ChangeItemPrompt() {
        super("ok", "!skip");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
            conversationContext.setSessionData("inventoryItem", ((Player) conversationContext.getForWhom()).getInventory().getItemInMainHand());
        }
        return new ChangeCmdPrompt();
    }

    @Override
    protected boolean isInputValid(@NotNull ConversationContext context, @NotNull String input) {
        return !((Player)context.getForWhom()).getInventory().getItemInMainHand().getType().equals(Material.AIR)
                && super.isInputValid(context, input)
            || input.equalsIgnoreCase("!skip");
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "To change the inventory item hold in main hand a new item and type 'ok'. To not change type '!skip'";
    }
}
