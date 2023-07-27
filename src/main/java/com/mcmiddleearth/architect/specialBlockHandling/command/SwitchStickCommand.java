package com.mcmiddleearth.architect.specialBlockHandling.command;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 *
 * @author Jubo
 */
public class SwitchStickCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (!(sender instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(sender);
            return true;
        }
        Player player = (Player)sender;
        if(PluginData.isModuleEnabled(player.getWorld(), Modules.CYCLE_BLOCKS)){
            if(!PluginData.hasPermission(player,Permission.SWITCH_STICK)){
                PluginData.getMessageUtil().sendNoPermissionError(sender);
                return true;
            }
            UUID uuid = player.getUniqueId();

            if(args.length < 1) {
                if(PluginData.isSwitchStick(uuid.toString())){
                    PluginData.saveStickEntry(uuid.toString(),false);
                    sendStickSwitchedMessage(sender,false);
                }else{
                    PluginData.saveStickEntry(uuid.toString(),true);
                    sendStickSwitchedMessage(sender,true);
                }
            }else if(args[0].equalsIgnoreCase("help")){
                int page = 1;
                if(args.length>1 && NumericUtil.isInt(args[1])) page = NumericUtil.getInt(args[1]);
                sendHelpMessage(player,page);
            }
            return true;
        }
        sendNotEnabledErrorMessage(sender);
        return true;
    }

    private void sendNotEnabledErrorMessage(CommandSender sender) {
        PluginData.getMessageUtil().sendErrorMessage(sender,"Cycle blocks is not enabled for this world.");
    }

    private void sendStickSwitchedMessage(CommandSender sender, Boolean stick){
        if(stick) {
            PluginData.getMessageUtil().sendInfoMessage(sender, "You switched the stick on.");
        }else{
            PluginData.getMessageUtil().sendInfoMessage(sender, "You switched the stick off.");
        }
    }

    @Override
    public String getHelpPermission(){ return Permission.SWITCH_STICK.getPermissionNode(); }

    @Override
    public String getShortDescription(){
        return ": Stick-Sticks Command.";
    }

    @Override
    public String getUsageDescription(){
        return ": Set command to disable the stick for block interaction. Block interaction is allowed by default.";
    }

    @Override
    protected void sendHelpMessage(Player player, int page){
        List<String[]> helpList = new ArrayList<>();
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"SwitchStick Command -";
        help = new String[][]{{"weselect ","",": Disables the stick for block interaction."}};
        helpList.addAll(Arrays.asList(help));
        help = helpList.toArray(help);
        super.sendHelpMessage(player,page);
    }
}