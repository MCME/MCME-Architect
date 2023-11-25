package com.mcmiddleearth.architect.additionalListeners;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.util.TheGafferUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public class LecternProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void lecternProtection(PlayerTakeLecternBookEvent event) {
//Logger.getGlobal().info("takeBook");
        Player player = event.getPlayer();
        event.setCancelled(true);
        ItemStack item = Objects.requireNonNull(event.getLectern().getInventory().getItem(0)).clone();
        item.setAmount(1);
        event.getPlayer().getInventory().addItem(item);
        if(TheGafferUtil.checkGafferPermission(player, event.getLectern().getLocation())
                || event.getPlayer().hasPermission(Permission.LECTERN_EDITOR.getPermissionNode())) {
            event.getLectern().getInventory().setItem(0,new ItemStack(Material.AIR));
        }
        event.getPlayer().closeInventory(InventoryCloseEvent.Reason.PLUGIN);
    }

    /**
     * Low event priority to get called before TheGaffer protection handler!
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void placeBookLectern(PlayerInteractEvent event) {
//Logger.getGlobal().info("placeBook" + event.getClickedBlock().getClass().getSimpleName());
//Logger.getGlobal().info("data "+ event.getClickedBlock().getBlockData() );
        if(event.getClickedBlock()!=null && event.getClickedBlock().getState() instanceof Lectern lectern) {
//Logger.getGlobal().info("isLectern");
//Logger.getGlobal().info("hasPermission" + event.getPlayer().hasPermission(Permission.LECTERN_EDITOR.getPermissionNode()));
//Logger.getGlobal().info("useItem "+event.useItemInHand());
//Logger.getGlobal().info("useBlock "+event.useInteractedBlock());
            if(TheGafferUtil.checkGafferPermission(event.getPlayer(), lectern.getLocation())
                    || event.getPlayer().hasPermission(Permission.LECTERN_EDITOR.getPermissionNode())) {
                BlockData data = event.getClickedBlock().getBlockData();
                if(data instanceof org.bukkit.block.data.type.Lectern lecternData) {
                    if(lecternData.isPowered() && lectern.getInventory().getItem(0)!=null) {
                        event.setCancelled(true);
                        event.getPlayer().openBook(Objects.requireNonNull(lectern.getInventory().getItem(0)));
                        Bukkit.getPluginManager().registerEvents(new Listener() {
                            @EventHandler
                            public void onEdit(PlayerEditBookEvent event) {
                                Objects.requireNonNull(lectern.getInventory().getItem(0)).setItemMeta(event.getNewBookMeta());
                                HandlerList.unregisterAll(this);
                            }
                        }, ArchitectPlugin.getPluginInstance());
                    }
                    if(!lecternData.hasBook()
                            && event.getItem()!=null
                            && (event.getItem().getType().equals(Material.WRITTEN_BOOK)
                                || event.getItem().getType().equals(Material.WRITABLE_BOOK))) {
//Logger.getGlobal().info("cancelEvent");
                        event.setCancelled(true);
                        ItemStack item = event.getItem().clone();
                        item.setAmount(1);
                        lectern.getInventory().setItem(0, item);
                        if (!event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
                            event.getItem().setAmount(event.getItem().getAmount() - 1);
                        }
                    }
                }
                /*&& event.getItem() != null
                && event.getItem().equals(lectern.getInventory().getItem(0))) {
                /*&& lectern.getInventory().isEmpty()
                && event.getItem() != null
                && (event.getItem().getType().equals(Material.WRITTEN_BOOK)
                    || event.getItem().getType().equals(Material.WRITABLE_BOOK))) {*/
                /*event.setCancelled(true);
                Bukkit.getScheduler().scheduleSyncDelayedTask(ArchitectPlugin.getPluginInstance(), ()-> {
                            Logger.getGlobal().info("hasBook" + !lectern.getInventory().isEmpty());
                            Logger.getGlobal().info("item" + event.getItem());
                            Arrays.stream(lectern.getInventory().getContents()).sequential().forEach(search -> Logger.getGlobal().info("inventory: " + search));
                            ItemStack item = event.getItem().clone();
                            item.setAmount(1);
                            lectern.getInventory().setItem(0, item);
                            if (!event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) {
                                event.getItem().setAmount(event.getItem().getAmount() - 1);
                            }
                        }, 1);*/
/*                Bukkit.getScheduler().scheduleSyncDelayedTask(ArchitectPlugin.getPluginInstance(),()->{
Logger.getGlobal().info("openInventory");
                    event.getPlayer().openInventory(lectern.getInventory());
                }, 1);*/
            }
        }
    }


}
