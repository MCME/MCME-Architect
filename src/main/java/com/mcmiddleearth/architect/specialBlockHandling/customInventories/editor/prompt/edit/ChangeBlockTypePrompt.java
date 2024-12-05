package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.BlockDataPrompt;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.ItemPrompt;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChangeBlockTypePrompt extends FixedSetPrompt {

    public ChangeBlockTypePrompt() {
        super("block", "bisected", "four_directions", "three_axis", "vanilla", "!skip");
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        if(!input.equalsIgnoreCase("!skip")) {
           conversationContext.setSessionData("type",input);
        }
        return switch(input) {
            case "block" -> new ChangeBlockDataPrompt("");
            case "bisected" -> new ChangeBlockDataPrompt("Up", "Down");
            case "four_directions" -> new ChangeBlockDataPrompt("North", "West", "South", "East");
            case "three_axis" -> new ChangeBlockDataPrompt("X", "Y", "Z");
            case "six_faces" -> new Chan
            case "vanilla" -> new ChangeItemPrompt();
            default -> END_OF_CONVERSATION;
        };
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Type in a new block type or '!skip'";
    }
}
