package com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor;

import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventory;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryCollectionState;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryState;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.add.BlockIdPrompt;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.prompt.edit.EditPrompt;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class CustomInventoryEditor {

    private static ConversationFactory addBlockConversationFactory;
    private static ConversationFactory editBlockConversationFactory;

    public static void init(Plugin plugin) {
        Map<Object,Object> sessionData = new HashMap<>();
        sessionData.put("blockData", new HashMap<String,String>());
        addBlockConversationFactory = new ConversationFactory(plugin)
                .withModality(false)
                .withEscapeSequence("!cancel")
                .withTimeout(300)
                .withLocalEcho(true)
                .withPrefix(conversationContext -> ChatColor.AQUA+"[Inventory Editor] ")
                .withInitialSessionData(sessionData)
                .addConversationAbandonedListener(new EditExecutor())
                .withFirstPrompt(new BlockIdPrompt());
        editBlockConversationFactory = new ConversationFactory(plugin)
                .withModality(false)
                .withEscapeSequence("!cancel")
                .withTimeout(300)
                .withLocalEcho(true)
                .withPrefix(conversationContext -> ChatColor.AQUA+"[Inventory Editor] ")
                .withInitialSessionData(sessionData)
                .addConversationAbandonedListener(new EditExecutor())
                .withFirstPrompt(new EditPrompt());
        }

    public static void addBlock(Player player, String rpName, String category, CustomInventoryState state, int slot) {
        Conversation conversation = addBlockConversationFactory.buildConversation(player);
        ConversationContext context = conversation.getContext();
        context.setSessionData("rpName",rpName);
        context.setSessionData("category", category);
        context.setSessionData("state", state);
        context.setSessionData("inCategory", true);
        context.setSessionData("slot", getLocator(slot));
        conversation.begin();
    }

    @SuppressWarnings("unchecked")
    public static void editBlock(Player player, String category, CustomInventoryState state, int slot, ItemStack inventoryItem) {
        Conversation conversation = editBlockConversationFactory.buildConversation(player);
        ConversationContext context = conversation.getContext();
        context.setSessionData("category", category);
        context.setSessionData("state", state);
        String blockId = SpecialBlockInventoryData.getSpecialBlockId(inventoryItem);
        if(blockId != null) {
            String[] rpAndId = blockId.split("/");
            context.setSessionData("id", rpAndId[1]);
            context.setSessionData("rpName", rpAndId[0]);
            ConfigurationSection inventoryConfig = getInventoryConfig(rpAndId[0], category);
            assert inventoryConfig != null;
            ConfigurationSection itemSection = inventoryConfig.getConfigurationSection(rpAndId[1]);
            assert itemSection != null;
            context.setSessionData("display", itemSection.get("display"));
            context.setSessionData("type", itemSection.get("type"));
            context.setSessionData("itemMaterial", itemSection.get("itemMaterial"));
            if (itemSection.contains("cmd")) {
                context.setSessionData("cmd", itemSection.get("cmd"));
            }
            if (itemSection.contains("color")) {
                context.setSessionData("color", itemSection.get("color"));
            }
            context.setSessionData("inCategory", itemSection.contains("category"));
            context.setSessionData("slot", getLocator(slot));
            Map<String, String> blockData = (Map<String, String>) context.getSessionData("blockData");
            assert blockData != null;
            itemSection.getKeys(false).stream().filter(key -> key.startsWith("blockData")).forEach(key -> {
                blockData.put(key.substring(9), itemSection.getString(key));
            });
            conversation.begin();
        }
    }

    @SuppressWarnings("unchecked")
    public static class EditExecutor implements ConversationAbandonedListener {
        @Override
        public void conversationAbandoned(@NotNull ConversationAbandonedEvent conversationAbandonedEvent) {
            Player player = (Player) conversationAbandonedEvent.getContext().getForWhom();
            if(conversationAbandonedEvent.gracefulExit()) {
                ConversationContext context = conversationAbandonedEvent.getContext();
                String rpName = (String) Objects.requireNonNull(context.getSessionData("rpName"));
                File rpFolder = new File(SpecialBlockInventoryData.configFolder,rpName);
                String category = (String) context.getSessionData("category"); assert category != null;
                File categoryFile = new File(rpFolder, category.toLowerCase()+".yml");
                if(categoryFile.exists()) {
                    YamlConfiguration config = new YamlConfiguration();
                    try {
                        config.load(categoryFile);
                        ConfigurationSection items = config.getConfigurationSection("Items");
                        assert items != null;
                        if(context.getSessionData("oldId")!=null) {
                            String oldId = (String) Objects.requireNonNull(context.getSessionData("oldId"));
                            if(items.contains(oldId)) {
                                items.set(oldId, null);
                            }
                        }
                        String blockId = (String) Objects.requireNonNull(context.getSessionData("id"));
                        if("delete".equalsIgnoreCase((String) context.getSessionData("action"))) {
                            items.set(blockId, null);
                        } else {
                            ConfigurationSection newSection = items.createSection(blockId);
                            newSection.set("display", context.getSessionData("display"));
                            newSection.set("type", Objects.requireNonNull(context.getSessionData("type")).toString()
                                    .toUpperCase());
                            newSection.set("itemMaterial", context.getSessionData("itemMaterial"));
                            if ((context.getSessionData("cmd") != null)) {
                                newSection.set("cmd", context.getSessionData("cmd"));
                            }
                            if ((context.getSessionData("color") != null)) {
                                newSection.set("color", context.getSessionData("color"));
                            }
                            Object inCategory = context.getSessionData("inCategory");
                            assert inCategory != null;
                            if ((Boolean) inCategory) {
                                newSection.set("category", context.getSessionData("category"));
                            }
                            if (context.getSessionData("state") instanceof CustomInventoryCollectionState collection) {
                                SpecialBlock baseBlock = collection.getBaseBlock();
                                String baseId = baseBlock.getId().substring(baseBlock.getId().indexOf("/") + 1);
                                Logger.getGlobal().info("BaseId: " + baseId);
                                ConfigurationSection baseSection = items.getConfigurationSection(baseId);
                                assert baseSection != null;
                                ConfigurationSection collectionSection = baseSection.getConfigurationSection("collection");
                                assert collectionSection != null;
                                String slot = (String) context.getSessionData("slot");
                                assert slot != null;
                                if (context.getSessionData("oldSlot") != null) {
                                    String oldSlot = (String) context.getSessionData("oldSlot");
                                    assert oldSlot != null;
                                    String otherBlockId = collectionSection.getString(slot);
                                    collectionSection.set(oldSlot, otherBlockId);
                                }
                                collectionSection.set(slot, blockId);
                                if(!newSection.contains("collection") && (Boolean) inCategory) {
                                    ConfigurationSection indirectCollection = newSection.createSection("collection");
                                    indirectCollection.set("indirect", baseBlock);
                                }
                            }

                            Map<String, String> blockData = (Map<String, String>) context.getSessionData("blockData");
                            assert blockData != null;
                            for (Map.Entry<String, String> entry : blockData.entrySet()) {
                                newSection.set("blockData" + entry.getKey(), entry.getValue());
                            }
                        }
                        config.save(categoryFile);
                        PluginData.getMessageUtil().sendInfoMessage(player, "Custom inventory item saved to file "
                                +rpName+"/"+categoryFile.getName()+" Reloading inventories...");
                        SpecialBlockInventoryData.loadInventories();
                        PluginData.getMessageUtil().sendInfoMessage(player, "Inventory reload done!");
                    } catch (IOException | InvalidConfigurationException e) {
                        PluginData.getMessageUtil().sendErrorMessage(player, "Internal error" +
                                " while saving custom inventory.");
                        e.printStackTrace();
                    }
                } else {
                    PluginData.getMessageUtil().sendErrorMessage(player,
                            "Can't save custom inventory. Inventory config file not found: "
                                    +rpName+"/"+categoryFile.getName());
                    Logger.getGlobal().warning("Not found! "+categoryFile);
                }
            } else {
                PluginData.getMessageUtil().sendErrorMessage(player, "Custom inventory editor conversation cancelled.");
            }
        }
    }

    private static String getLocator(int slot) {
        slot = slot - CustomInventory.CATEGORY_SLOTS;
        char letter;
        int number;
        if(slot % 9 < 5) {
            letter = switch (slot / 9) {
                case 0 -> 'A';
                case 1 -> 'B';
                case 2 -> 'C';
                case 3 -> 'D';
                case 4 -> 'E';
                default -> 'X';
            };
            number = 4 - slot % 9;
        } else {
            letter = switch (slot / 9) {
                case 0 -> 'F';
                case 1 -> 'G';
                case 2 -> 'H';
                case 3 -> 'I';
                case 4 -> 'J';
                default -> 'X';
            };
            number = slot % 9 - 4;
        }
        return ""+letter+number;
    }

    private static ConfigurationSection getInventoryConfig(String rpName, String category) {
        File rpFolder = new File(SpecialBlockInventoryData.configFolder, rpName);
        File categoryFile = new File(rpFolder, category.toLowerCase() + ".yml");
        if (categoryFile.exists()) {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(categoryFile);
                return config.getConfigurationSection("Items");
            } catch (IOException | InvalidConfigurationException e) {
                Logger.getGlobal().warning("Error while reading inventory config! " + categoryFile);
                e.printStackTrace();
                return null;
            }
        } else {
            Logger.getGlobal().warning("Not found! " + categoryFile);
            return null;
        }
    }
}
