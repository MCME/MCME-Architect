package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryCollectionState;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.DisplayPrompt;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeDisplayPrompt extends DisplayPrompt {

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Current description is "+conversationContext.getSessionData("display")
                +". Type in new description for the custom inventory or type '!skip'.";
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext conversationContext, @Nullable String input) {
        if(input != null && !input.equalsIgnoreCase("!skip")) {
            conversationContext.setSessionData("display", input);
        }
        if(conversationContext.getSessionData("state") instanceof CustomInventoryCollectionState) {
            return new ChangeCategoryVisiblePrompt();
        } else {
            return END_OF_CONVERSATION;
        }
    }
}
