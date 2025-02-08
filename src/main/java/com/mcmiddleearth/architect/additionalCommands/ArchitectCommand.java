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
package com.mcmiddleearth.architect.additionalCommands;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.blockData.BlockDataManager;
import com.mcmiddleearth.architect.entityLogging.EntityLogger;
import com.mcmiddleearth.pluginutil.nms.AccessInventory;
import com.mcmiddleearth.pluginutil.nms.NMSUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Eriol_Eandur
 */
public class ArchitectCommand extends AbstractArchitectCommand{

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            if(!(sender instanceof Player)) {
                PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
                return true;
            }
            if(!PluginData.hasPermission(sender,Permission.ARCHITECT_HELP)) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            if(args.length<2 || NumericUtil.isInt(args[1])) {
                int page = 1;
                if(args.length>1 && NumericUtil.isInt((args[1]))) {
                    page = NumericUtil.getInt(args[1]);
                }
                List<FancyMessage> commandList = new ArrayList<>();
                FancyMessage header = new FancyMessage(MessageType.INFO,PluginData.getMessageUtil())
                                                .addSimple("Help for "
                                                            +PluginData.getMessageUtil().STRESSED+"MCME Architect"
                                                            +PluginData.getMessageUtil().INFO+" commands.");
                for(String commandKey: ArchitectPlugin.getCommandList()) {
                    AbstractArchitectCommand executor = (AbstractArchitectCommand) ArchitectPlugin
                                                           .getPluginInstance().getCommand(commandKey).getExecutor();
                    if(sender.hasPermission(executor.getHelpPermission())) {
                        String shortHelp = executor.getShortDescription();
                        String usageHelp = executor.getUsageDescription();
                        String clickCommand = executor.getHelpCommand();
                        boolean runAtOnce = true;
                        if(clickCommand==null) {
                            clickCommand = "/"+commandKey+" ";
                            runAtOnce = false;
                        } 
                        FancyMessage cmdLine = new FancyMessage(MessageType.WHITE,PluginData.getMessageUtil())
                            .addFancy(ChatColor.DARK_AQUA+"/"+commandKey,
                                        clickCommand, 
                                        PluginData.getMessageUtil()
                                                .hoverFormat("/"+commandKey+usageHelp,":",true))
                            .addClickable(ChatColor.WHITE+shortHelp, clickCommand);
                        if(runAtOnce) {
                            cmdLine.setRunDirect();
                        }
                        commandList.add(cmdLine);
                    }
                }
                PluginData.getMessageUtil().sendFancyListMessage((Player)sender, header, commandList, 
                                                                 "/architect help", page);
                sendManualMessage(sender);
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("checkNBT")) {
            if((sender instanceof Player) && !PluginData.hasPermission(sender, Permission.CHECK_NBT)) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            if(args.length > 1) {
                if(Bukkit.getPlayer(args[1])!=null) {
                    PluginData.getMessageUtil().sendInfoMessage(sender, "Items of player "+ChatColor.GREEN+args[1]+ChatColor.AQUA+" with NBT:");
                    checkInventory(sender, Bukkit.getPlayer(args[1]));
                } else {
                    PluginData.getMessageUtil().sendErrorMessage(sender, "Player " + ChatColor.DARK_RED+args[1]+ChatColor.RED +" needs to be online for item NBT checking.");
                }
            } else if(sender instanceof Player) {
                PluginData.getMessageUtil().sendInfoMessage(sender, "NBT of your main hand item:");
                String nbt = getNBT(sender,((Player)sender).getInventory().getItemInMainHand());
                PluginData.getMessageUtil().sendNoPrefixInfoMessage(sender, 
                        nbt.equals("")?"No item NBT found.":""
                                       +ChatColor.GREEN+((Player)sender).getInventory().getItemInMainHand().getType()+": "+ChatColor.AQUA+nbt);
            } else {
                PluginData.getMessageUtil().sendErrorMessage(sender, "From console you need to add a player name.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("weather")) {
            if(!(sender instanceof Player)) {
                PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
                return true;
            } 
            if(!PluginData.hasPermission(sender, Permission.ARCHITECT_WEATHER)) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(sender);
                return true;
            }
            if(args[1].equalsIgnoreCase("clear")) {
                PluginData.setOverrideWeather(true);
                ((Player)sender).getWorld().setStorm(false);
                ((Player)sender).getWorld().setThundering(false);
                PluginData.getMessageUtil().sendInfoMessage(sender, "Weather changing to clear.");
                return true;
            } else if(args[1].equalsIgnoreCase("rain")) {
                PluginData.setOverrideWeather(true);
                ((Player)sender).getWorld().setStorm(true);
                PluginData.getMessageUtil().sendInfoMessage(sender, "Weather changing to rain.");
                return true;
            } else if(args[1].equalsIgnoreCase("thunder")) {
                PluginData.setOverrideWeather(true);
                ((Player)sender).getWorld().setStorm(true);
                PluginData.getMessageUtil().sendInfoMessage(sender, "Weather changing to thunder.");
                return true;
            }
            PluginData.getMessageUtil().sendInvalidSubcommandError(sender);
            return true;
        }
        if(!(sender instanceof ConsoleCommandSender 
                || (sender instanceof Player 
                    && (PluginData.hasPermission(sender, Permission.ARCHITECT_INFO)
                         || PluginData.hasPermission(sender, Permission.ARCHITECT_RELOAD))))) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        if(args[0].equalsIgnoreCase("blockdata")) {
            if(!(sender instanceof Player)) {
                PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
                return true;
            }
            Block block = ((Player)sender).getTargetBlock(null, 100);
            if(block!=null) {
                PluginData.getMessageUtil().sendInfoMessage(sender, "Block Data for "
                                                  +block.getX()+" "+block.getY()+" "+block.getZ()
                                                  +": "+block.getBlockData().getAsString());
            } else {
                PluginData.getMessageUtil().sendErrorMessage(sender, "Target block needs to be visible!");
            }
            return true;
        }
        if(args[0].equalsIgnoreCase("dev")) {
            if(args.length>1 && args[1].equalsIgnoreCase("true")) {
                DevUtil.setConsoleOutput(true);
                showDetails(sender);
                return true;
            }
            else if(args.length>1 && args[1].equalsIgnoreCase("false")) {
                DevUtil.setConsoleOutput(false);
                showDetails(sender);
                return true;
            }
            else if(args.length>1) {
                try {
                    int level = Integer.parseInt(args[1]);
                    DevUtil.setLevel(level);
                    showDetails(sender);
                    return true;
                }
                catch(NumberFormatException e){}
            }
            if(sender instanceof Player) {
                Player player = (Player) sender;
                if(args.length>1 && args[1].equalsIgnoreCase("-r")) {
                    DevUtil.remove(player);
                    showDetails(sender);
                    return true;
                }
                DevUtil.add(player);
                showDetails(sender);
                return true;
            }
            return true;
        }
        if (args[0].toLowerCase().startsWith("world")) {
            PluginData.getMessageUtil().sendInfoMessage(sender,"Worlds:");
            for(String name: PluginData.getWorldNames()) {
                if(sender instanceof Player) {
                    new FancyMessage(MessageType.INFO_INDENTED,PluginData.getMessageUtil())
                            .addClickable("- "+PluginData.getMessageUtil().STRESSED+name, "/mvtp "+name)
                            .send((Player) sender);
                } else {
                    PluginData.getMessageUtil().sendNoPrefixInfoMessage(sender, "- "+name);
                }
            }
            return true;
        /*} else  if (args[0].toLowerCase().startsWith("saveworld")) {
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(sender);
                return true;
                }
            World world = Bukkit.getWorld(args[1]);
            if(world!=null) {
                world.save();
                PluginData.getMessageUtil().sendInfoMessage(sender, "World '"+world.getName()+"' saved.");
                return true;
            }
            PluginData.getMessageUtil().sendErrorMessage(sender,"World not found.");
            return true;*/
        } 
        if(!(sender instanceof ConsoleCommandSender 
                    || PluginData.hasPermission(sender, Permission.ARCHITECT_RELOAD))) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        if(args[0].equalsIgnoreCase("placeAllBlockStates")) {
            BlockDataManager.placeAllBlocksStates(((Player)sender), 
                        ((Player)sender).getLocation().getBlock().getRelative(BlockFace.SOUTH), false,
                        (args.length>1 && args[1].equalsIgnoreCase("file")));
            return true;
        }
        if(args[0].equalsIgnoreCase("eLog")) {
            if(args.length>1 && args[1].equalsIgnoreCase("true")) {
                EntityLogger.setLogging(true,((Player)sender).getWorld());
                PluginData.getMessageUtil().sendInfoMessage(sender, "Entity logging on!");
            } else {
                EntityLogger.setLogging(false,((Player)sender).getWorld());
                PluginData.getMessageUtil().sendInfoMessage(sender, "Entity logging off!");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("reloaddata")) {
            Bukkit.getServer().reloadData();
            PluginData.getMessageUtil().sendInfoMessage(sender, "Reloading mc server data...");
        } else if (args[0].equalsIgnoreCase("version")) {
            PluginData.getMessageUtil().sendInfoMessage(sender, "Version: "+ ArchitectPlugin.getPluginInstance()
                                                     .getDescription().getVersion());
        } else if (args[0].equalsIgnoreCase("reload")) {
            if(!(sender instanceof ConsoleCommandSender 
                    || PluginData.hasPermission(sender, Permission.ARCHITECT_RELOAD))) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            ArchitectPlugin.getPluginInstance().loadData();
            PluginData.getMessageUtil().sendInfoMessage(sender,  "Reload complete!");
        /*} else if (args[0].equalsIgnoreCase("biome")) {
            Location loc = ((Player) sender).getLocation();
            Block block = loc.getBlock().getRelative(BlockFace.NORTH, 3);
            for (int i= 0; i<4; i++) {
                block.setBiome(Biome.SWAMP);
                block.getRelative(BlockFace.UP,5).setBiome(Biome.FOREST);
                block.getRelative(BlockFace.UP,10).setBiome(Biome.DESERT);
                block = block.getRelative(BlockFace.UP);
            }*/
        } else {
            PluginData.getMessageUtil().sendInvalidSubcommandError(sender);
        }
        return true;
    }
    
    /*@Override
    public List<String> onTabComplete(CommandSender sender,
                                               Command command,
                                               java.lang.String alias,
                                               java.lang.String[] args) {
        List<String> result = new ArrayList<>();
        result.add("placeallblockstates");
        return result;
    }*/
    
    private void showDetails(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs,"DevUtil: Level - "+DevUtil.getLevel()+"; Console - "+DevUtil.isConsoleOutput()+"; ");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"Developer:");
        for(OfflinePlayer player:DevUtil.getDeveloper()) {
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs, "            "+player.getName());
        }
    }
    
   private void sendManualMessage(CommandSender cs) {
        cs.sendMessage(PluginData.getMessageUtil().HIGHLIGHT_STRESSED
                +"http://www.mcmiddleearth.com/resources/mcme-architect-manual.95");
    }


    private String getNBT(CommandSender sender, ItemStack item) {
        String nbt = "";
        if(item!=null) {
            nbt = AccessInventory.getItemNBT(item).toString();
            /*try {
                Object nmsItem = NMSUtil.getCraftBukkitDeclaredField("inventory.CraftItemStack","handle",item);
                Object tag = NMSUtil.invokeNMS("ItemStack", "getTag", new Class[]{}, nmsItem);
                nbt = NBTTagUtil.asString(tag);
            } catch(NullPointerException ex) {}*/
        }
        return nbt;
    }
    
    private void checkInventory(CommandSender sender, Player player) {
        player.getInventory().forEach(item -> {
            String nbt = getNBT(sender,item);
            if(!nbt.equals("")) {
                PluginData.getMessageUtil().sendNoPrefixInfoMessage(sender, ""+ChatColor.GREEN+item.getType()+": "+ChatColor.AQUA+nbt);
            }
        });
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.ARCHITECT_INFO.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Information about MCME Architect.";
    }

    @Override
    public String getUsageDescription() {
        return " help | world | dev | version | reload [#page]: Argument 'help' shows information about Architect commands. 'world' shows a list of all server worlds. 'dev' switches on/off debug messages. 'version' displays Architect version. 'reload' reloads Architect plugin.";
    }

}
