package com.mcmiddleearth.architect.specialBlockHandling.recipeBookInventory;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialHeadInventoryData;
import com.mcmiddleearth.pluginutil.NMSUtil;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRecipeBookSettingsChangeEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

public class TestRecipeBookListener implements Listener {

    @EventHandler
    public void onStonecutterRecipeSelect(PlayerStonecutterRecipeSelectEvent event) {
        Logger.getGlobal().info("PlayerStonecutterRecipeSelect Event!");
    }

    @EventHandler
    public void onRecipeBookSettingsChange(PlayerRecipeBookSettingsChangeEvent event) {
        Logger.getGlobal().info("PlayerRecipeBookSettingsChange Event!");
        //direct reopen messes up inventory
        // -> reopen recipe book on inventory close event!
        //openRecipeBook(event.getPlayer());
    }

    @EventHandler
    public void onRecipeDiscover(PlayerRecipeDiscoverEvent event) {
        //Logger.getGlobal().info("PlayerRecipeDiscover Event!");
    }

    @EventHandler
    public void onRecipeBookClick(PlayerRecipeBookClickEvent event) {
        Logger.getGlobal().info("PlayerRecipeBookClick Event!");
        event.setCancelled(true);
        if(event.getPlayer().isSneaking()) {//doesn't work
            event.getPlayer().getOpenInventory().setCursor(new ItemStack(Material.STICK, 2));
        } else {
            Logger.getGlobal().info("Recipe: "+event.getRecipe());
            ItemStack item = Objects
                    .requireNonNull(Bukkit.getRecipe(event.getRecipe())).getResult();
            InventoryView inventoryView = event.getPlayer().getOpenInventory();
            if(inventoryView.getTopInventory() instanceof CraftingInventory craftingInventory) {
                ItemStack[] matrix = craftingInventory.getMatrix();
                matrix[4] = item;
                craftingInventory.setMatrix(matrix);
                craftingInventory.setResult(item);
            }
            item = item.clone();
            item.setAmount(2);
            event.getPlayer().getOpenInventory().setCursor(item);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
Logger.getGlobal().info("Inventory click! Type: "+event.getClick().name());
Logger.getGlobal().info("Action: "+ event.getAction().name());
        if(event.getClickedInventory() instanceof CraftingInventory inventory) {
Logger.getGlobal().info("CraftingInventory click!!");
            event.setCancelled(true);
            if(event.getCurrentItem()!=null) {
                if(event.getCurrentItem().getType().equals(Material.STONE)) {
                    /*Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()->{
                        event.getWhoClicked().getOpenInventory().close();
                    },1);*/
                    updatePlayerRecipes((Player) event.getWhoClicked(),true);
                    openInv((Player) event.getWhoClicked(),inventory);
                } else if(event.getCurrentItem().getType().equals(Material.DIRT)) {
                    updatePlayerRecipes((Player) event.getWhoClicked(),false);
                    openInv((Player) event.getWhoClicked(),inventory);
                } else {
                    ItemStack item = event.getCurrentItem().clone();
                    item.setAmount(2);
                    event.getWhoClicked().getOpenInventory().setCursor(item);
                }
            }
        }
    }

    private static void openInv(Player player, CraftingInventory inventory) {
        inventory.setMatrix(new ItemStack[9]);
        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()->{
            player.openWorkbench(null,true);
            Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),() -> {
                ItemStack[] matrix = inventory.getMatrix();
                matrix[1] = new ItemStack(Material.STONE);
                matrix[7] = new ItemStack(Material.DIRT);
                inventory.setMatrix(matrix);
            },1);
        },2);
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if(event.getPlayer() instanceof Player player) {
            configureRecipeBook(player);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
Logger.getGlobal().info("Open Inventory");
        if(event.getInventory() instanceof CraftingInventory inventory) {
Logger.getGlobal().info("Crafting!");
            ItemStack[] matrix = inventory.getMatrix();
            matrix[1] = new ItemStack(Material.STONE);
            matrix[7] = new ItemStack(Material.DIRT);
            inventory.setMatrix(matrix);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerInteractEvent event) {
        if(EquipmentSlot.HAND.equals(event.getHand())
                &&event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.DIAMOND)) {
            event.setCancelled(true);
            configureRecipeBook(event.getPlayer());
            updatePlayerRecipes(event.getPlayer(),false);
            //updateecipeBook(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(), () -> {
            configureRecipeBook(event.getPlayer());
            updatePlayerRecipes(event.getPlayer(), false);
        },5);
    }

    public static void loadRecipeBook() {
        Bukkit.clearRecipes();
        Map<NamespacedKey, Recipe> recipes = getAllRecipes();
        for(Map.Entry<NamespacedKey,Recipe> entry: recipes.entrySet()) {
            Bukkit.addRecipe(entry.getValue(),false);
        }
        Bukkit.updateRecipes();
    }

    private static Map<NamespacedKey, Recipe> getAllRecipes() {
        Map<NamespacedKey, Recipe> recipes = SpecialBlockInventoryData.getRecipes("Human");
        recipes.putAll(SpecialHeadInventoryData.getRecipes());
        return recipes;
    }

    public static void updatePlayerRecipes(Player player, boolean heads) {
        Bukkit.updateRecipes();
        Logger.getGlobal().info("Undiscover: "+player.undiscoverRecipes(getAllRecipes().keySet()));
        Map<NamespacedKey, Recipe> recipes;
        if(heads) {
            recipes = SpecialHeadInventoryData.getRecipes();
        } else {
            recipes = SpecialBlockInventoryData.getRecipes("Human");
        }
        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()-> {
            Logger.getGlobal().info("Discover: "+player.discoverRecipes(recipes.keySet()));
        },1);
    }

    public static void configureRecipeBook(Player player) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.RECIPES);
        try {
            Class<?> recipeBookType = NMSUtil.getNMSClass("world.inventory.RecipeBookType");
            Object types = NMSUtil.invokeNMS("world.inventory.RecipeBookType", "values",
                    new Class[]{}, null);
            for (int i = 0; i < Array.getLength(types); i++) {
                Object type = Array.get(types, i);
                Object recipeBookSettings = packet.getModifier().read(3);
                NMSUtil.invokeNMS("stats.RecipeBookSettings", "a", new Class[]{recipeBookType, boolean.class},
                        recipeBookSettings, type, true);
                NMSUtil.invokeNMS("stats.RecipeBookSettings", "b", new Class[]{recipeBookType, boolean.class},
                        recipeBookSettings, type, false);
            }
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void addPacketListener() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                ArchitectPlugin.getPluginInstance(),
                ListenerPriority.NORMAL,
                PacketType.Play.Server.RECIPES
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
Logger.getGlobal().info("Sending packet: "+event.getPacket().getType());
                PacketContainer packet = event.getPacket();
                Object action = packet.getModifier().read(0);
                try {
                    Method values = action.getClass().getDeclaredMethod("values");
                    Object actionValues = values.invoke(action);
                    //Object actionValues = NMSUtil.invokeNMS(action.getClass().getName(),"values",new Class[]{},action);
    //Logger.getGlobal().info("RecipeUpdate a: "+(action == Array.get(actionValues, 0)));
    //Logger.getGlobal().info("RecipeUpdate b: "+(action == Array.get(actionValues, 1)));
    //Logger.getGlobal().info("RecipeUpdate c: "+(action == Array.get(actionValues, 2)));
                    List<Integer> listOne = packet.getIntLists().read(0);
                    if(action == Array.get(actionValues,0)){
                        List<Integer> listTwo = packet.getIntLists().read(1);
                        listOne.addAll(listTwo);
                        listTwo.clear();
                        //packet.getIntLists().write(1, Collections.emptyList());
                    } else if(action == Array.get(actionValues, 1)) {
                        packet.getModifier().write(0, Array.get(actionValues, 0));
                    }
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPacketReceiving(PacketEvent event) {
                Logger.getGlobal().info("Receiving packet: "+event.getPacket().getType());
            }

        });
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                ArchitectPlugin.getPluginInstance(),
                ListenerPriority.NORMAL,
                PacketType.Play.Server.AUTO_RECIPE,
                PacketType.Play.Server.RECIPE_UPDATE, PacketType.Play.Client.AUTO_RECIPE,
                /*PacketType.Play.Client.RECIPE_DISPLAYED, */PacketType.Play.Client.RECIPE_SETTINGS
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Logger.getGlobal().info("Sending packet: " + event.getPacket().getType());
            }

            @Override
            public void onPacketReceiving(PacketEvent event) {
                Logger.getGlobal().info("Receiving packet: " + event.getPacket().getType());
                if(event.getPacket().getType().equals(PacketType.Play.Client.RECIPE_SETTINGS)) {
                    Logger.getGlobal().info("ConfigureRecipeBook!");
                    Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),() -> {
                        configureRecipeBook(event.getPlayer());
                        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()->
                            openInv(event.getPlayer(),
                                (CraftingInventory) event.getPlayer().getOpenInventory().getTopInventory()),1);
                    },1);
                }
            }
        });
    }
}
