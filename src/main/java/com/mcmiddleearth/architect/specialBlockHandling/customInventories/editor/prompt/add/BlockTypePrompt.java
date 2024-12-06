package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add;

import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.FixedSetPrompt;
import org.bukkit.conversations.Prompt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class BlockTypePrompt extends FixedSetPrompt {

    public static final String[][] blockData = new String[][]{
            {"block", ""},
            {"bisected", "Up", "Down"},
            {"four_directions", "North", "West", "South", "East"},
            {"six_faces", "North", "West", "South", "East", "Up", "Down"},
            {"five_faces", "North", "West", "South", "East", "Up"},
            {"three_axis", "X", "Y", "Z"},
            {"branch_twigs",},
            {"branch_horizontal",},
            {"diagonal_connect", "North", "West", "South", "East", "Up" },
            {"block_connect",""},
            {"double_y_block","Upper", "Lower"},
            {"eight_faces", "North", "NorthWest", "West", "SouthWest", "South", "SouthEast", "East", "NorthEast"},
            {"upshift",""},
            {"block_on_water",""},
            {"vanilla"}
    };

    public BlockTypePrompt() {
        super(getBlockTypes());
    }

    @Override
    protected @Nullable Prompt acceptValidatedInput(@NotNull ConversationContext conversationContext, @NotNull String input) {
        conversationContext.setSessionData("type",input);
        for (String[] blockDatum : blockData) {
            if (input.equalsIgnoreCase(blockDatum[0])) {
                String[] blockOrientations = Arrays.copyOfRange(blockDatum, 1, blockDatum.length);
                if(blockOrientations.length == 0) {
                    return new ItemPrompt();
                } else {
                    return new BlockDataPrompt(blockOrientations);
                }
            }
        }
        return END_OF_CONVERSATION;
        /*return switch(input) {
            case "block" -> new BlockDataPrompt("");
            case "bisected" -> new BlockDataPrompt("Up", "Down");
            case "four_directions" -> new BlockDataPrompt("North", "West", "South", "East");
            case "three_axis" -> new BlockDataPrompt("X", "Y", "Z");
            case "six_faces" -> new BlockDataPrompt("North", "West", "South", "East", "Up", "Down");
            case "branch_twigs" -> ;
            case "branch_horizontal" ->

            case "vanilla" -> new ItemPrompt();
            default -> END_OF_CONVERSATION;
        };*/
    }

    @Override
    public @NotNull String getPromptText(@NotNull ConversationContext conversationContext) {
        return "Which type of block do you want to add? "+formatFixedSet()+" ";
    }

    public static String[] getBlockTypes() {
        String[] result = new String[blockData.length];
        for(int i = 0; i < blockData.length; i++) {
            result[i] = blockData[i][0];
        }
        return result;
    }
}
