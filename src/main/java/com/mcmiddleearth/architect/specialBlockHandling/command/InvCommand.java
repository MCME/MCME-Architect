/*
 * Copyright (C) 2016 MCME
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialBlockInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialItemInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialHeadInventoryData;
import com.mcmiddleearth.architect.specialBlockHandling.data.SpecialSavedInventoryData;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.serverResoucePack.RpManager;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.CustomInventoryCategory;
import com.mcmiddleearth.architect.specialBlockHandling.specialBlocks.SpecialBlock;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.util.ZipUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class InvCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender cs, Command command, String label, String[] args) {
        final CommandSender sender = cs;
        if(args.length>0 && args[0].equalsIgnoreCase("reload")) {
            if((cs instanceof Player) && !PluginData.hasPermission((Player)cs,Permission.INV_RELOAD_COMMAND)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            final List<String> argList = new ArrayList<>();
            argList.addAll(Arrays.asList(Arrays.copyOfRange(args, 1, args.length)));
            final CommandSender csFinal = cs;
//Logger.getGlobal().info("args argList "+args.length + " "+argList.size()+" "+argList.toString());
            BukkitRunnable downloader = new BukkitRunnable() {
                BukkitRunnable asyncDownloader;
                ZipInputStream inputStream;
                int counter;
                String rpName;
                
                @Override
                public void run() {
//Logger.getGlobal().info("task timer ");
                    if(asyncDownloader == null //no download in progress
                            || !(Bukkit.getScheduler().isCurrentlyRunning(asyncDownloader.getTaskId())
                                 || (Bukkit.getScheduler().isQueued(asyncDownloader.getTaskId())))){
//Logger.getGlobal().info("start download ");
                        while(argList.size()>0 && !argList.get(0).startsWith("rp:")) {
//Logger.getGlobal().info("argList "+argList.get(0));
                            argList.remove(0);
                        }
                        if(argList.isEmpty()) { //nothing more to download
                            SpecialBlockInventoryData.loadInventories();
                            SpecialItemInventoryData.loadInventories();
                            SpecialHeadInventoryData.loadInventory();
                            SpecialSavedInventoryData.loadInventories();
                            sendInventoryLoadedMessage(csFinal);
                            cancel();
                        } else { //still something to download
                            rpName = RpManager.matchRpName(argList.get(0).substring(3));
                            if(rpName.equals("")) {
                                sendNotAValidRpKey(csFinal);
                            } else { //start next rp download
                                asyncDownloader = new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        PluginData.getMessageUtil().sendInfoMessage(sender, "Downloading config files for rp "+rpName+".");
                                        try {
                                            int count = SpecialBlockInventoryData.downloadConfig(rpName,inputStream);
                                            PluginData.getMessageUtil().scheduleInfoMessage(sender, "Downloaded "+count+" block inventory files for rp "+rpName+".");
                                        } catch (IOException ex) {
                                            Logger.getLogger(ZipUtil.class.getName()).log(Level.SEVERE, null, ex);
                                            PluginData.getMessageUtil()
                                                      .scheduleErrorMessage(sender, "Error while downloading block config files for rp "
                                                                       +rpName+".");
                                        }
                                        try {
                                            int count = SpecialItemInventoryData.downloadConfig(rpName,inputStream);
                                            PluginData.getMessageUtil().scheduleInfoMessage(sender, "Downloaded "+count+" item inventory files for rp "+rpName+".");
                                        } catch (IOException ex) {
                                            PluginData.getMessageUtil()
                                                      .scheduleErrorMessage(sender, "Error while downloading item config files for rp "
                                                                       +rpName+".");
                                        }
                                    }
                                };
                                asyncDownloader.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
                                argList.remove(0);
                            }
                        }
                    } else { //download in progress
                        if(counter < 12) { //keep waiting (12 * 5 sec timeout)
                            PluginData.getMessageUtil().sendInfoMessage(sender, "Downloading config files for rp "+rpName+".");
//Logger.getGlobal().info("wait for download ");
                            counter++;
                        } else { //cancel download
                            try {
                                inputStream.close();
                            } catch (IOException ex) {
                                Logger.getLogger(InvCommand.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            PluginData.getMessageUtil().sendErrorMessage(sender, "Downloading of config files for rp "+rpName+" timed out.");
                            asyncDownloader.cancel();
                            counter = 0;
                        }
                    }
                }
            };
            downloader.runTaskTimer(ArchitectPlugin.getPluginInstance(), 0, 100);
            return true;
        }
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player p = (Player) cs;
        if(!PluginData.isModuleEnabled(p.getWorld(), Modules.SPECIAL_BLOCKS_GET)) {
            sendNotEnabledErrorMessage(cs);
            return true;
        }
        if(!PluginData.hasPermission(p,Permission.INV_COMMAND)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(args.length==0 || args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            sendHelpMessage((Player)cs,page);
            return true;
        }
        if(args[0].startsWith("h")) {
            if(args.length>1 && args[1].startsWith("s:")) {
                /*if(args.length<3) {
                    PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                    return true;
                }*/
                SpecialHeadInventoryData.openSearchInventory(p,args[1].substring(2));
                return true;
            }
            SpecialHeadInventoryData.openInventory(p);
            return true;
        }
        int rpIndex = 0;
        String rpName = "";
        for(int i=1; i<args.length;i++) {
            if(args[i].startsWith("rp:")) {
                rpName = RpManager.matchRpName(args[i].substring(3));
                if(rpName.equals("")) {
                    sendNotAValidRpKey(p);
                    return true;
                }
                rpIndex = i;
                break;
            }
        }
        if(rpIndex == 0) {
            rpName = RpManager.getCurrentRpName(p);//PluginData.getRpName(ResourceRegionsUtil.getResourceRegionsUrl(p));
            if(rpName.equals("")) {
                sendNotInRpRegion(p);
                return true;
            }
        }
        if(args[0].equalsIgnoreCase("create")) {
            if(!PluginData.hasPermission(p,Permission.INV_SAVE)) {
                PluginData.getMessageUtil().sendNoPermissionError(p);
                return true;
            }
            if(args.length<=adaptIndex(1,rpIndex)) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                return true;
            }
            if(SpecialSavedInventoryData.categoryExists(args[adaptIndex(1,rpIndex)], rpName)) {
                sendInventoryAlreadyExistsError(p);
                return true;
            }
            SpecialSavedInventoryData.saveInventory(p, args[adaptIndex(1,rpIndex)], rpName, true);
            sendInventorySavedMessage(p);
            return true;
        }
        if(args[0].equalsIgnoreCase("delete")) {
            if(!PluginData.hasPermission(p,Permission.INV_SAVE)) {
                PluginData.getMessageUtil().sendNoPermissionError(p);
                return true;
            }
            if(args.length<=adaptIndex(1,rpIndex)) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                return true;
            }
            if(!SpecialSavedInventoryData.categoryExists(args[adaptIndex(1,rpIndex)], rpName)) {
                sendInventoryNotFoundError(p);
                return true;
            }
            CustomInventoryCategory category = SpecialSavedInventoryData
                                                 .getCategory(args[adaptIndex(1,rpIndex)], rpName);
            if(!category.getOwner().equals(p.getUniqueId()) 
                        && !PluginData.hasPermission(p, Permission.INV_OTHER)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
            }
            SpecialSavedInventoryData.deleteInventory(args[adaptIndex(1,rpIndex)], rpName);
            sendInventoryDeletedMessage(p);
            return true;
        }
        if(args[0].startsWith("s") || args[0].startsWith("c")) {
            SpecialSavedInventoryData.openInventory(p, rpName);
            return true;
        }
        boolean search = false;
        String searchText = "";
        if(args.length>adaptIndex(1,rpIndex) && (args[0].startsWith("b") || args[0].startsWith("i"))) {
            if(args[adaptIndex(1,rpIndex)].startsWith("s:")) {
                search=true;
                searchText = args[adaptIndex(1,rpIndex)].substring(2);
                /*if(args.length<=adaptIndex(2,rpIndex)) {
                    PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                    return true;
                }*/
            }
        }
        /*if(searchIndex==0) {
            if(args.length>2 && args[2].equalsIgnoreCase("search")) {
                searchIndex = 3;
                if(args.length<4) {
                    PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                    return true;
                }
            }
        }*/
        if(args[0].startsWith("b")) {
            /*if(!SpecialBlockInventoryData.hasBlockInventory(rpName)) {
                sendBlockInventoryNotFound(p);
                return true;
            }*/
            if(search) {
                if(!SpecialBlockInventoryData.openSearchInventory(p, rpName, searchText)) {//;//args[adaptIndex(2,rpIndex)]);
                    sendBlockInventoryNotFound(p);
                }
            } else {
                if(!SpecialBlockInventoryData.openInventory(p, rpName)) {
                    sendBlockInventoryNotFound(p);
                }
            }
            return true;
        } 
        if(args[0].startsWith("i")) {
            PluginData.getMessageUtil().sendErrorMessage(sender, "Not implemented yet!");
            return true;
            /*if(!SpecialItemInventoryData.hasItemInventory(rpName)) {
                sendItemInventoryNotFound(p);
                return true;
            }
            if(search) {
                SpecialItemInventoryData.openSearchInventory(p, rpName, searchText);//args[adaptIndex(2,rpIndex)]);
            } else {
                SpecialItemInventoryData.openInventory(p, rpName);
            }
            return true;*/
        }
        if(sender instanceof Player && sender.isOp() && args[0].equals("testItemBlock")) {
            Logger.getGlobal().info("place item block test area");
            SpecialBlock data = SpecialBlockInventoryData.getSpecialBlock(args[1]);
            Block start = ((Player)sender).getLocation().getBlock();
            start = start.getRelative(BlockFace.SOUTH);
            for(int i = 0; i<=NumericUtil.getInt(args[2]);i+=NumericUtil.getInt(args[3])) {
                for(int j=0; j<NumericUtil.getInt(args[2]);j+=NumericUtil.getInt(args[3])) {
                    
                    data.placeBlock(start.getRelative(i,0,j), BlockFace.UP, (Player)sender);
                }
            }
        }
        PluginData.getMessageUtil().sendInvalidSubcommandError(p);
        return true;
        /*if(args[1].equals("search")) {
            String rpName="";
            String search = "";
            if(args.length<3) {
                rpName = PluginData.getRpName(ResourceRegionsUtil.getResourceRegionsUrl(p));
                if(rpName.equals("")) {
                    sendNotInRpRegion(p);
                    return true;
                }
                search = args[1];
            } else {
                rpName = PluginData.matchRpName(args[1]);
                if(rpName.equals("")) {
                    sendNotAValidRpKey(p);
                    return true;
                }
                search = args[2];
            }
            if(args.length>3 && args[3].startsWith("-i")) {
                if(!SpecialItemInventoryData.hasItemInventory(rpName)) {
                    sendItemInventoryNotFound(p);
                    return true;
                }
                SpecialItemInventoryData.openSearchInventory(p, rpName, search);
                return true;
            } else {
                if(!SpecialBlockInventoryData.hasBlockInventory(rpName)) {
                    sendBlockInventoryNotFound(p);
                    return true;
                }
                SpecialBlockInventoryData.openSearchInventory(p, rpName, search);
                return true;
            }
        }*/
    }
    
    private int adaptIndex(int index, int rpIndex) {
        if(rpIndex==0) {
            return index;
        } else if(rpIndex<=index) {
            return index+1;
        } else {
            return index;
        }
    }
    
    private void sendNotEnabledErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Custom build inventories are not enabled in this world.");
    }
    
    private void sendNotInRpRegion(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "You are not in a valid resource pack region. Please use '/inv <subcommand> <rp:rp-name>'.");
    }
    
    private void sendBlockInventoryNotFound(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "No block inventory found for this resource pack.");
    }
    
    private void sendNotAValidRpKey(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Resource pack not found.");
    }
    
    private void sendInventoryLoadedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Special Blocks inventory reloaded");
    }

    private void sendInventorySavedMessage(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p, "Inventory saved.");
    }

    private void sendInventoryDeletedMessage(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p, "Inventory deleted.");
    }

    private void sendItemInventoryNotFound(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "No item inventory found for this resource pack.");
    }
    
    private void sendInventoryAlreadyExistsError(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p,"An inventory with that name already exists."); 
    }

    private void sendInventoryNotFoundError(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p,"No inventory with that name."); 
    }

    @Override
    public String getHelpPermission() {
        return Permission.INV_COMMAND.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Handles MCME custom inventories.";
    }
    @Override
    public String getUsageDescription() {
        return ": Opens block, item and head inventories. Creates and manages custom block inventories.\n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/inv help";
    }

    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"command /inv ... -";
        help = new String[][]{
                   {"/inv b"," [rp:<rpName>] [s:<search>]",": Open MCME block inventory.","Without optional parameter [rp:<rpName>] that inventory is opened which matches to the resource region you are in. Optional parameter [s:<search>] opens an inventory with all blocks which names contain <search>."},
                   //{"/inv i"," [rp:<rpName>] [s:<search>]",": Open MCME item inventory.","Without optional parameter [rp:<rpName>] that inventory is opened which matches to the resource region you are in. Optional parameter [s:<search>] opens an inventory with all items which names contain <search>."},
                   {"/inv c"," [rp:<rpName>]",": Open custom block inventory.","Without optional parameter [rp:<rpName>] that inventory is opened which matches to the resource region you are in. If you are not in a resource region (plotworld, Themed-builds) inventory for Gondor pack opens."},
                   {"/inv h"," [s:<search>]",": Open MCME head inventory."," Heads don't depend on resource packs, so no additional resource pack parameter here. Optional parameter [s:<search>] opens an inventory with all heads which names contain <search>."},
                   {"/inv create"," [rp:<rpName>] <name>",": Saves your inventory"," as a new custom block inventory. This is meant for Project leaders for example to create inventories with all blocks needed for a plot build."},
                   {"/get delete"," [rp:<rpName>] <name>",": Deletes a previously created (with /inv create) customized inventory."},
                   {"/get reload"," [rp:<rpName1>] [rp:<rpName2>] [...]",": Reloads all inventories from config files."},
                };
        super.sendHelpMessage(player, page);
    }

    
}
