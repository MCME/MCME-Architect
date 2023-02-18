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
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class UndoCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(@NotNull CommandSender cs, @NotNull Command cmd, @NotNull String c, String[] args) {
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
        if(!CopyPasteManager.hasUndos(player)) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Nothing to undo.");
            return true;
        }
        int undos = 1;
        if(args.length>0 && NumericUtil.isInt(args[0])) {
            undos = NumericUtil.getInt(args[0]);
        }
        int undid;
        try {
            undid = CopyPasteManager.undoEdits(player, undos);
            PluginData.getMessageUtil().sendInfoMessage(cs, "Successuly reverted "+undid+" edit"+(undid!=1?"s":"")+".");
        } catch (CopyPasteException ex) {
            PluginData.getMessageUtil().sendErrorMessage(cs, ex.getMessage());
        }
        //PluginData.getMessageUtil().sendErrorMessage(cs, "There was an error. Undo might be partial or derped.");
        return true;
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.COPY_PASTE.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Undo edits.";
    }

    @Override
    public String getUsageDescription() {
        return " [#n]: Undo up to #n previous edits.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/undo";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"undo command -";
        help = new String[][]{{"It's really simple just /undo."}};
        super.sendHelpMessage(player, page);
    }

}
