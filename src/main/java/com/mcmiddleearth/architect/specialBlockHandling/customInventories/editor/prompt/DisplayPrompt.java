package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DisplayPrompt extends StringPrompt {

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Enter a block description for the custom inventory:";
    }

    @Override
    public @Nullable Prompt acceptInput(@NotNull ConversationContext conversationContext, @Nullable String input) {
        conversationContext.setSessionData("display", input);
        return END_OF_CONVERSATION;
    }
}
