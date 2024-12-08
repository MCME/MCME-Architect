package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.BlockIdPrompt;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeBlockIdPrompt extends BlockIdPrompt {

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Current item id is "+conversationContext.getSessionData("id")+". Enter a new id or '!skip'.";
    }

    @Override
    public @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
            conversationContext.setSessionData("oldId", conversationContext.getSessionData("id"));
            conversationContext.setSessionData("id",input);
        }
        return new ChangeBlockTypePrompt();
    }
}
