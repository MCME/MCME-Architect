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
package com.mcmiddleearth.architect.voxelStencilEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.FileUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.confirmation.ConfirmationFactory;
import com.mcmiddleearth.pluginutil.confirmation.Confirmationable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class SlCommand extends AbstractArchitectCommand implements Confirmationable {

    public static HashMap<UUID, StencilList> stencilLists = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        Player player = (Player)sender;
        if(!PluginData.hasPermission(player, Permission.STENCIL_LIST_EDITOR)) {
            PluginData.getMessageUtil().sendNoPermissionError(sender);
            return true;
        }
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.STENCIL_LIST_EDITOR)) {
            sendNotEnabledMessage(player);
            return true;
        }
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            sendHelpMessage(player,page);
            return true;
        }
        if (args[0].equalsIgnoreCase("save")) {
            StencilList list = stencilLists.get(player.getUniqueId());
            if(list==null) {
                sendNoCurrentStencilList(player);
                return true;
            }
            if(list.getStencils().size()<1) {
                sendNoStenicilInListMessage(player);
                return true;
            }
            if(list.fileExists()) {
                String query = "Stencil list "+ChatColor.YELLOW+list.getName()
                               +ChatColor.GOLD+" already exists. Do you want to overwrite it?";
                new ConfirmationFactory(ArchitectPlugin.getPluginInstance(),PluginData.getMessageUtil())
                        .start(player, query, this, new Object[]{list});
            } else {
                confirmed(player,new Object[]{list});
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("show")) {
            StencilList list = stencilLists.get(player.getUniqueId());
            if(list==null) {
                sendNoCurrentStencilList(player);
                return true;
            }
            sendShowStencilsMessage(player,list.getStencils());
            return true;
        }
        if (args.length < 2) {
            PluginData.getMessageUtil().sendNotEnoughArgumentsError(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            stencilLists.put(player.getUniqueId(), new StencilList(args[1]));
            sendListCreatedMessage(player, args[1]);
            return true;
        }
        if (args[0].equalsIgnoreCase("load")) {
            StencilList list =  StencilList.loadFromFile(args[1]);
            if(list==null) {
                sendStencilListNotFoundErrorMessage(player);
                return true;
            }
            stencilLists.put(player.getUniqueId(),list);
            sendListLoadedMessage(player, args[1]);
            return true;
        }
        StencilList list = stencilLists.get(player.getUniqueId());
        if(list==null) {
            sendNoCurrentStencilList(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("add")) {
            int count = addStencils(list, args[1]);
            sendStencilAddedMessage(player, count, list.getName());
        } else if (args[0].equalsIgnoreCase("remove")) {
            int count = removeStencils(list, args[1]);
            sendStencilsRemovedMessage(player, count, list.getName());
        } else {
            PluginData.getMessageUtil().sendInvalidSubcommandError(player);
        }
        return true;
    }
    
    @Override
    public void confirmed(Player player, Object[] data) {
        StencilList list = (StencilList)data[0];
        if(list.saveToFile()){
            sendListSavedMessage(player, list.getName(), list.getStencils().size());    
        } else {
            sendIOErrorSaveMessage(player);
        }
    }

    @Override
    public void cancelled(Player player, Object[] data) {
        sendSaveListCancelled(player);
    }
    
    private int addStencils(StencilList list, String stencilName) {
        File search = new File(VoxelConstants.STENCILS_DIR+"/"+stencilName);
        File dir;
        if(search.isDirectory()) {
            dir = search;
        } else {
            dir = new File(search.getParent());
        }
        File[] stencils = dir.listFiles(FileUtil.getFileExtFilter(VoxelConstants.STENCIL_EXT));
        if(stencils==null) {
            return 0;
        }
        int count = 0;
        for(File stencil: stencils) {
            String relativePath = FileUtil.getRelativePath(stencil,VoxelConstants.STENCILS_DIR);
            relativePath = relativePath.substring(0, relativePath.lastIndexOf("."));
            relativePath = relativePath.replace('\\', '/');
            String regex = stencilName.replaceAll("\\*", ".*");
            if(relativePath.matches(regex)) {
                if(list.addStencil(relativePath)) {
                    count ++;
                }
            }
        }
        return count;
    }
    
    private int removeStencils(StencilList list, String stencilName) {
        List<String> removeList = new ArrayList<>();
        int count=0;
        for(String search: list.getStencils()) {
            if(search.matches(stencilName.replaceAll("\\*", ".*"))) {
                removeList.add(search);
                count++;
            }
        }
        list.getStencils().removeAll(removeList);
        return count;
    }

    private void sendNotEnabledMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player, "Stencil List Editor is not enabled for this world.");
    }

    private void sendNoCurrentStencilList(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"You have to create or load a stencil list first.");
    }

    private void sendListCreatedMessage(Player player, String name) {
        PluginData.getMessageUtil().sendInfoMessage(player,"You created a new stencil list: "+PluginData.getMessageUtil().STRESSED+name);
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(player, "You can now add or remove stencils.");
    }

    private void sendStencilListNotFoundErrorMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"Stencil list not found.");
    }

    private void sendListLoadedMessage(Player player, String name) {
        PluginData.getMessageUtil().sendInfoMessage(player,"Stencil list was loaded from file: "+PluginData.getMessageUtil().STRESSED+name);
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(player, "You can now add or remove stencils.");
    }

    private void sendStencilAddedMessage(Player player, int count, String name) {
        if(count <1) {
            sendNoStencilsFoundMessage(player);
            return;
        }
        String stencils;
        if(count >1) {
            stencils = "stencils were";
        } else {
            stencils = "stencil was";
        }
        PluginData.getMessageUtil().sendInfoMessage(player, ""+PluginData.getMessageUtil().STRESSED+count
                                           +PluginData.getMessageUtil().INFO+" "+stencils+" added "
                                           +"to stencil list " 
                                           +PluginData.getMessageUtil().STRESSED+name);
    }

    private void sendStencilsRemovedMessage(Player player, int count, String name) {
        if(count <1) {
            sendNoStencilsFoundMessage(player);
            return;
        }
        String stencils;
        if(count >1) {
            stencils = "stencils were";
        } else {
            stencils = "stencil was";
        }
        PluginData.getMessageUtil().sendInfoMessage(player, ""+PluginData.getMessageUtil().STRESSED+count
                                           +PluginData.getMessageUtil().INFO+" "+stencils+" removed "
                                           +"from stencil list " 
                                           +PluginData.getMessageUtil().STRESSED+name);
    }

    private void sendNoStencilsFoundMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player, "No stencils found.");
    }

    private void sendListSavedMessage(Player player, String name, int size) {
        PluginData.getMessageUtil().sendInfoMessage(player,"The stencil list "+PluginData.getMessageUtil().STRESSED
                                           +name+PluginData.getMessageUtil().INFO+" has been saved with "
                                           +PluginData.getMessageUtil().STRESSED+size+PluginData.getMessageUtil().INFO
                                           +" stencil"+(size==1?"":"s")+".");
    }

    private void sendNoStenicilInListMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player, "Add at least one stencil to your list first.");
    }

    private void sendSaveListCancelled(Player player) {
        PluginData.getMessageUtil().sendInfoMessage(player, "You cancelled saving of your stencil list.");
    }

    private void sendShowStencilsMessage(Player player, List<String> list) {
        if(list.isEmpty()) {
            PluginData.getMessageUtil().sendInfoMessage(player,"No stencils in your list.");
            return;
        }
        PluginData.getMessageUtil().sendInfoMessage(player, "Stencils in your current stencil list:");
        for(String string: list) {
            PluginData.getMessageUtil().sendNoPrefixInfoMessage(player, "- "+string);
        }
    }

    private void sendIOErrorSaveMessage(Player player) {
        PluginData.getMessageUtil().sendErrorMessage(player,"There was an error while saving.");
    }

    @Override
    public String getHelpPermission() {
        return Permission.STENCIL_LIST_EDITOR.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Stencil List Editor.";
    }

    @Override
    public String getUsageDescription() {
        return ": Create new stencil lists. Add and remove stencils from exsiting stencil lists. \n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/sl help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Voxel Stencil List Editor -";
        help = new String[][]{{"/sl create"," <listName>",": Creates a new stencil list."},
                                       {"/sl load"," <listName>",": Loads a stencil list."},
                                       {"/sl add ","<stencilName>",": Adds a stencil to list."},
                                       {"/sl remove ","<stencilName>",": Removes a stencil from list."},
                                       {"/sl save ","<listName>",": Saves stencil list."},
                                       {"/sl show","",": Shows stencils in current list."}};
        super.sendHelpMessage(player, page);
    }

}
