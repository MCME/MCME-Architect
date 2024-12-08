package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.ValidatingPrompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockIdPrompt extends ValidatingPrompt {

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Type in an unique ID for the new item! You may '!cancel' at any point of the conversation.";
    }

    @Override
    protected @Nullable String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
        return "You need to type in an item id that doesn't exist yet.";
    }

    @Override
    protected boolean isInputValid(@NotNull ConversationContext conversationContext, @NotNull String input) {
        return SpecialBlockInventoryData.getSpecialBlock(conversationContext.getSessionData("rpName")+"/"+input)
                == null;
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        conversationContext.setSessionData("id", input);
        return new BlockTypePrompt();
    }
}
