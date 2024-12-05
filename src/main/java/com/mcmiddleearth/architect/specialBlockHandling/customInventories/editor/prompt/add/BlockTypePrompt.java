package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;

public class BlockTypePrompt extends FixedSetPrompt {

    public static final String[][] blockData = new String[][]{
            {"block", ""},
            {"bisected", "Up", "Down"},
            {"four_directions", "North", "West", "South", "East"}
    };
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
            case "six_faces" -> new BlockDataPrompt("North", "West", "South", "East", "Up", "Down");
            case "branch_twigs" -> ;
            case "branch_horizontal" ->

            case "vanilla" -> new ItemPrompt();
            default -> END_OF_CONVERSATION;
        };
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Which type of block do you want to add? "+formatFixedSet()+" ";
    }
}
