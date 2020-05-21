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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class PasteCommand extends AbstractArchitectCommand {

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
        if(!CopyPasteManager.hasClipboard(player)) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Copy something to your clipboard first with /copy.");
            return true;
        }
        boolean withAir = true;
        boolean withBiome = true;
        for(String arg: args) {
            if(arg.equals("-a")) {
                withAir = false;
            }
            if(arg.equals("-b")) {
                withBiome = false;
            }
        }
        try {
            if(CopyPasteManager.pasteClipboard(player, withAir, withBiome)) {
                PluginData.getMessageUtil().sendInfoMessage(cs, "Your clipboard was pasted.");
            } else {
                PluginData.getMessageUtil().sendErrorMessage(cs, "There was an error. Your clipboard was not pasted.");
                return true;
            }
        } catch (CopyPasteException ex) {
            PluginData.getMessageUtil().sendErrorMessage(cs, ex.getMessage());
        }
        return true;
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.COPY_PASTE.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Paste your clipboard.";
    }

    @Override
    public String getUsageDescription() {
        return ": Paste your clipboard to your current location.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/paste";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Paste command -";
        help = new String[][]{{"It's really simple just /paste."}};
        super.sendHelpMessage(player, page);
    }

}
