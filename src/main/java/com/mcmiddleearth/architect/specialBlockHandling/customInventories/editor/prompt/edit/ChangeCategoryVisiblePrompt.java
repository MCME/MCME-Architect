package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeCategoryVisiblePrompt extends FixedSetPrompt {

    public ChangeCategoryVisiblePrompt() {
        super("yes", "no", "!skip");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
            conversationContext.setSessionData("inCategory", input.equalsIgnoreCase("yes"));
        }
        return END_OF_CONVERSATION;
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Should the inventory item be listed directly in the category of it's collection? "+formatFixedSet();
    }
}
