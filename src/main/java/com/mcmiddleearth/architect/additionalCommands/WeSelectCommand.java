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

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 *
 * @author Jubo
 */
public class WeSelectCommand extends AbstractArchitectCommand {

    private final static Map<UUID,String> weSelect = new HashMap<>();
    private final static Map<UUID,String> weSelectShift = new HashMap<>();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        Player player = (Player)sender;
        if(PluginData.isModuleEnabled(player.getWorld(), Modules.SPECIAL_BLOCKS_FLINT))
        {
            if(!PluginData.hasPermission(player, Permission.WE_SELECT)) {
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            UUID uuid = player.getUniqueId();

            if (!weSelect.containsKey(uuid)) {
                weSelect.put(uuid,"");
            }
            if(!weSelectShift.containsKey(uuid)) {
                weSelectShift.put(uuid, "");
            }

            if(args[0].equalsIgnoreCase("help")){
                int page = 1;
                if(args.length>1 && NumericUtil.isInt(args[1])) page = NumericUtil.getInt(args[1]);
                sendHelpMessage(player,page);
            }else if(args[0].equalsIgnoreCase("reset")){
                weSelect.replace(uuid,"");
                weSelectShift.replace(uuid,"");
                sendStringReset(sender);
            } else if(args[0].equalsIgnoreCase("show")){
                sendShow(sender,weSelectShift.get(uuid),weSelect.get(uuid));
            } else if(args[0].equalsIgnoreCase("shift")) {
                if(args.length > 2){
                    String args_added = "";
                    for(int i = 1; i < args.length;i++) args_added = args_added + " " + args[i];
                    args_added = args_added.substring(1);
                    weSelectShift.replace(uuid,args_added);
                    sendStringSet(sender,args_added,true);
                }else {
                    weSelectShift.replace(uuid, args[1]);
                    sendStringSet(sender,args[1],true);
                }
            } else {
                if(args.length > 1){
                    String args_added = "";
                    for(int i = 0;i < args.length; i++) args_added = args_added + " " + args[i];
                    args_added = args_added.substring(1);
                    weSelect.replace(uuid,args_added);
                    sendStringSet(sender,args_added,false);
                }else {
                    weSelect.replace(uuid, args[0]);
                    sendStringSet(sender,args[0],false);
                }
            }
            return true;
        }
        sendNotEnabledErrorMessage(player);
        return true;
    }

    public static String getWeSelect(UUID player, boolean shift){
        if (!weSelect.containsKey(player)) {
            weSelect.put(player,"");
        }
        if(!weSelectShift.containsKey(player)){
            weSelectShift.put(player,"");
        }

        if(shift) return weSelectShift.get(player);
        else return weSelect.get(player);
    }

    private void sendNotEnabledErrorMessage(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"WE block data selection is not enabled for this world.");
    }

    private void sendStringReset(CommandSender cs){
        PluginData.getMessageUtil().sendInfoMessage(cs,"The command was reset.");
    }

    private void sendShow(CommandSender cs,String sneaking, String no_sneaking){
        PluginData.getMessageUtil().sendInfoMessage(cs,"You set the command: '"+no_sneaking+"' to left click.");
        PluginData.getMessageUtil().sendInfoMessage(cs,"You set the command: '"+sneaking+"' to shift left click");
    }

    private void sendStringSet(CommandSender cs, String command, boolean shift){
        if(!shift) PluginData.getMessageUtil().sendInfoMessage(cs,"You set the command: '"+command+"' to left click.");
        else PluginData.getMessageUtil().sendInfoMessage(cs,"You set the command: '"+command+"' to shift left click");
    }

    @Override
    public String getHelpPermission() {
        return Permission.WE_SELECT.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": WE-Select Command.";
    }

    @Override
    public String getUsageDescription() {
        return ": Set command before left-clicking block info in chat. # can work as placeholder for the info.";
    }

    @Override
    protected void sendHelpMessage(Player player, int page){
        List<String[]> helpList = new ArrayList<>();
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"WeSelect Command -";
        help = new String[][]{
                {"/weselect show","",": Shows the currently set commands."},
                {"/weselect reset","",": Resets the commands to nothing."},
                {"/weselect ","<command>",": Sets the command for normal left click."},
                {"/weselect shift ","<command>",": Sets the command for shift left lick."}};
        helpList.addAll(Arrays.asList(help));
        help = helpList.toArray(help);
        super.sendHelpMessage(player,page);
    }
}
