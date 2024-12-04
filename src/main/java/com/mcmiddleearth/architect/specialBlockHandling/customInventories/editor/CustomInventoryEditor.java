package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor;

import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryState;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.BlockIdPrompt;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.BlockTypePrompt;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class CustomInventoryEditor {

    private static ConversationFactory addBlockconversationFactory;

    public static void init(Plugin plugin) {
        Map<Object,Object> sessionData = new HashMap<>();
        sessionData.put("blockData", new HashMap<String,String>());
        addBlockconversationFactory = new ConversationFactory(plugin)
                .withModality(false)
                .withEscapeSequence("!cancel")
                .withTimeout(120)
                .withLocalEcho(true)
                .withPrefix(conversationContext -> "[Inventory Editor] ")
                .withInitialSessionData(sessionData)
                .addConversationAbandonedListener(new AddBlockExecutor())
                .withFirstPrompt(new BlockIdPrompt());
        }

    public static void addBlock(Player player, String rpName, String category, CustomInventoryState state) {
        Conversation conversation = addBlockconversationFactory.buildConversation(player);
        ConversationContext context = conversation.getContext();
        context.setSessionData("rpName",rpName);
        context.setSessionData("category", category);
        context.setSessionData("state", state);
        conversation.begin();
    }

    public static class AddBlockExecutor implements ConversationAbandonedListener {
        @Override
        public void conversationAbandoned(@NotNull ConversationAbandonedEvent conversationAbandonedEvent) {
            if(conversationAbandonedEvent.gracefulExit()) {
                ConversationContext context = conversationAbandonedEvent.getContext();
                File rpFolder = new File(SpecialBlockInventoryData.configFolder,
                        (String) Objects.requireNonNull(context.getSessionData("rpName")));
                File categoryFile = new File(rpFolder, context.getSessionData("category")+".yml");
                if(categoryFile.exists()) {
                    YamlConfiguration config = new YamlConfiguration();
                    try {
                        config.load(categoryFile);
                        ConfigurationSection items = config.getConfigurationSection("Items");
                        assert items != null;
                        ConfigurationSection newSection = items.createSection((String) Objects.requireNonNull(context
                                                                                .getSessionData("id")));
                        newSection.set("display", context.getSessionData("display"));
                        newSection.set("type", Objects.requireNonNull(context.getSessionData("type")).toString()
                                                                                                .toUpperCase());

                    } catch (IOException | InvalidConfigurationException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
