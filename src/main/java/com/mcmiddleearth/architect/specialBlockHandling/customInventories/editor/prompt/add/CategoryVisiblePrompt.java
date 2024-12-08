package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CategoryVisiblePrompt extends FixedSetPrompt {

    public CategoryVisiblePrompt() {
        super("yes", "no");
    }

    protected CategoryVisiblePrompt(@NotNull String... fixedSet) {
        super(fixedSet);
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Should the new inventory item be listed directly in the category of it's collection. "+formatFixedSet();
    }

    @Override
    protected @Nullable String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
        return "If you type in 'no' the item will be available only by a block collection.";
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        conversationContext.setSessionData("inCategory", input.equalsIgnoreCase("yes"));
        return END_OF_CONVERSATION;
    }
}
