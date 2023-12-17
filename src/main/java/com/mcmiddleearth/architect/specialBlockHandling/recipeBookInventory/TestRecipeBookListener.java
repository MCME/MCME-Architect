package com.mcmiddleearth.architect.specialBlockHandling.recipeBookInventory;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRecipeBookSettingsChangeEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public class TestRecipeBookListener implements Listener {

    @EventHandler
    public void onStonecutterRecipeSelect(PlayerStonecutterRecipeSelectEvent event) {
        Logger.getGlobal().info("PlayerStonecutterRecipeSelect Event!");
    }

    @EventHandler
    public void onRecipeBookClick(PlayerRecipeBookClickEvent event) {
        Logger.getGlobal().info("PlayerRecipeBookClick Event!");
        event.setCancelled(true);
        if(event.getPlayer().isSneaking()) {
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
//Logger.getGlobal().info("Inventory click!");
        if(event.getClickedInventory() instanceof CraftingInventory inventory) {
//Logger.getGlobal().info("CraftingInventory click!");
            event.setCancelled(true);
            if(event.getCurrentItem()!=null) {
                if(event.getCurrentItem().getType().equals(Material.STONE)) {
                    Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()->{
                        event.getWhoClicked().getOpenInventory().close();
                    },1);
                    resetRecipeBook((Player) event.getWhoClicked(),true);
                    openInv((Player) event.getWhoClicked());
                } else if(event.getCurrentItem().getType().equals(Material.DIRT)) {
                    resetRecipeBook((Player) event.getWhoClicked(),false);
                    openInv((Player) event.getWhoClicked());
                } else {
                    ItemStack item = event.getCurrentItem().clone();
                    item.setAmount(2);
                    event.getWhoClicked().getOpenInventory().setCursor(item);
                }
            }
        }
    }

    private void openInv(Player player) {
        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()->{
            player.openInventory(Bukkit.createInventory(player, InventoryType.WORKBENCH));
        },2);
    }


    @EventHandler
    public void onRecipeBookSettingsChange(PlayerRecipeBookSettingsChangeEvent event) {
        Logger.getGlobal().info("PlayerRecipeBookSettingsChange Event!");
        //direct reopen messes up inventory
        // -> reopen recipe book on inventory close event!
        //openRecipeBook(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if(event.getPlayer() instanceof Player player) {
            openRecipeBook(player);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if(event.getInventory() instanceof CraftingInventory inventory) {
            ItemStack[] matrix = inventory.getMatrix();
            matrix[1] = new ItemStack(Material.STONE);
            matrix[7] = new ItemStack(Material.DIRT);
            inventory.setMatrix(matrix);
        }
    }

    @EventHandler
    public void onRecipeDiscover(PlayerRecipeDiscoverEvent event) {
        //Logger.getGlobal().info("PlayerRecipeDiscover Event!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {


        openRecipeBook(event.getPlayer());
        resetRecipeBook(event.getPlayer(), false);
    }

    public static void resetRecipeBook(Player player, boolean heads) {
        Bukkit.clearRecipes();
Logger.getGlobal().info("1");
        Map<NamespacedKey, Recipe> recipes;
        if(!heads) {
            recipes = SpecialBlockInventoryData.getRecipes(RpManager.getCurrentRpName(player));
        } else {
            recipes = SpecialHeadInventoryData.getRecipes();
        }
        for(Map.Entry<NamespacedKey,Recipe> entry: recipes.entrySet()) {
            Bukkit.addRecipe(entry.getValue(),false);
        }
Logger.getGlobal().info("2");
        //Bukkit.updateRecipes();
        Bukkit.getScheduler().runTaskLater(ArchitectPlugin.getPluginInstance(),()-> {
            player.discoverRecipes(recipes.keySet());
Logger.getGlobal().info("3");
        },5);
    }

    public static void openRecipeBook(Player player) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.RECIPES);
                /*new PacketType(PacketType.Play.getProtocol(),
                PacketType.Play.Server.getSender(),
                63, 61, Bukkit.getServer()));*/
        /*Logger.getGlobal().info(String.valueOf(packet.getType().getCurrentId()));
        Logger.getGlobal().info("Ints: " + packet.getIntegers().size());
        Logger.getGlobal().info("Boolean: " + packet.getBooleans().size());
        Logger.getGlobal().info("Bytes: " + packet.getBytes().size());
        Logger.getGlobal().info("Modifiers: " + packet.getModifier().size());
        Logger.getGlobal().info("Double: " + packet.getDoubles().size());
        Logger.getGlobal().info("LOng: " + packet.getLongs().size());
        Logger.getGlobal().info("Modifiers: " + packet.getModifier().size());
        Logger.getGlobal().info("GameModes: " + packet.getGameModes().size());
        Logger.getGlobal().info("IntList: " + packet.getIntLists().size());
        Logger.getGlobal().info("ByteArray: " + packet.getByteArrays().size());

        Logger.getGlobal().info("Modifier Classe:" + packet.getModifier().read(0).getClass().getName());
        Logger.getGlobal().info("Modifier Classe:" + packet.getModifier().read(1).getClass().getName());
        Logger.getGlobal().info("Modifier Classe:" + packet.getModifier().read(2).getClass().getName());
        Logger.getGlobal().info("Modifier Classe:" + packet.getModifier().read(3).getClass().getName());
        Logger.getGlobal().info("INtlist size:" + packet.getIntLists().read(0).size() + packet.getIntLists().read(1).size());*/
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
        /*ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                ArchitectPlugin.getPluginInstance(),
                ListenerPriority.NORMAL,
                PacketType.Play.Server.RECIPES
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
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
        });*/
    }
}
