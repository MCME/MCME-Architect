package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditPrompt extends FixedSetPrompt {

    public EditPrompt() {
        super("edit", "delete", "none");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        conversationContext.setSessionData("action",input);
        if(input.equalsIgnoreCase("edit")) {
            return new ChangeBlockIdPrompt();
        } else {
            return END_OF_CONVERSATION;
        }
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "What do you want to do with inventory entry "+conversationContext.getSessionData("id")+"? "+formatFixedSet();
    }
}
