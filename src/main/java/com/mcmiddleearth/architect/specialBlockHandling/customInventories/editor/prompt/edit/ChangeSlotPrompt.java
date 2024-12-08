package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeSlotPrompt extends FixedSetPrompt {

    public ChangeSlotPrompt() {
        super("A0", "A1", "A4", "B2", "B4", "C2", "C4", "D2", "D4", "E0", "E1", "E4",
              "F1", "F4", "G2", "G4", "H2", "H4", "I2", "I4", "J1", "J4", "!skip");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
            conversationContext.setSessionData("oldSlot", conversationContext.getSessionData("slot"));
            conversationContext.setSessionData("slot", input);
        }
        return END_OF_CONVERSATION;
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Current item slot is "+conversationContext.getSessionData("slot")
                +". Type in a new slot for the inventory item or '!skip'. "+formatFixedSet();
    }

    @Override
    protected @Nullable String getFailedValidationText(@NotNull ConversationContext context, @NotNull String invalidInput) {
        return "You need to type in a slot label or '!skip'.";
    }
}
