/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.copyPaste;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class RedoCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player player = (Player) cs;
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.COPY_PASTE)) {
            PluginData.getMessageUtil().sendErrorMessage(cs,"CopyPasting is not enabled for this world.");
            return true;
        }
        if(!PluginData.hasPermission(cs, Permission.COPY_PASTE)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(!CopyPasteManager.hasRedos(player)) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Nothing to redo.");
            return true;
        }
        int redos = 1;
        if(args.length>0 && NumericUtil.isInt(args[0])) {
            redos = NumericUtil.getInt(args[0]);
        }
        int redid;
        try {
            redid = CopyPasteManager.redoEdits(player, redos);
            PluginData.getMessageUtil().sendInfoMessage(cs, "Successuly restored "+redid+" edit"+(redid!=1?"s":"")+".");
        } catch (CopyPasteException ex) {
            PluginData.getMessageUtil().sendErrorMessage(cs, ex.getMessage());
        }
        //   PluginData.getMessageUtil().sendErrorMessage(cs, "There was an error. Restored areas might be partial or derped.");
        
        return true;
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.COPY_PASTE.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Redo edits.";
    }

    @Override
    public String getUsageDescription() {
        return " [#n]: Redo up to #n previously undone edits.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/redo";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"redo command -";
        help = new String[][]{{"It's really simple just /redo."}};
        super.sendHelpMessage(player, page);
    }

}
