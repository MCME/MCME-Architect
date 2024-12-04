package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockTypePrompt extends FixedSetPrompt {

    public BlockTypePrompt() {
        super("block", "bisected", "four_directions", "three_axis", "vanilla");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        conversationContext.setSessionData("type",input);
        return switch(input) {
            case "block" -> new BlockDataPrompt("");
            case "bisected" -> new BlockDataPrompt("Up", "Down");
            case "four_directions" -> new BlockDataPrompt("North", "West", "South", "East");
            case "three_axis" -> new BlockDataPrompt("X", "Y", "Z");
            default -> END_OF_CONVERSATION;
        };
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Which type of block do you want to add? "+formatFixedSet()+" ";
    }
}
