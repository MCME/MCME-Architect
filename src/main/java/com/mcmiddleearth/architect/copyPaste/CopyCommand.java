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
import com.mcmiddleearth.pluginutil.WEUtil;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import java.util.logging.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class CopyCommand extends AbstractArchitectCommand {

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
        Region weRegion = WEUtil.getSelection((Player)cs);
        if(weRegion==null) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Please make a WE selection first.");
            return true;
        }
        if(!(weRegion instanceof CuboidRegion)) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Only cuboid selections are supported.");
            return true;
        }
        int area = weRegion.getArea();
        int maxArea = CopyPasteManager.maxAllowedSize(player);
        if(area > maxArea && !(player.hasPermission(Permission.COPY_PASTE_UNLIMITED.getPermissionNode()))) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Your selections is too large: "+area+" blocks."
                                                             +"You are allowed to copy "+maxArea+" block only.");
            return true;
        }
        try{
//Logger.getGlobal().info("1");
            if(CopyPasteManager.copyToClipboard(player, (CuboidRegion) weRegion)) {
                PluginData.getMessageUtil().sendInfoMessage(cs, "Your selection was copied to your clipboard.");
            } else {
                PluginData.getMessageUtil().sendErrorMessage(cs, "There was an error. Your selection was not copied.");
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
        return ": Copy to clipboard.";
    }

    @Override
    public String getUsageDescription() {
        return ": Copy your cuboid WE selection to your clipboard.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/copy";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Copy command -";
        help = new String[][]{{"It's really simple just /copy."}};
        super.sendHelpMessage(player, page);
    }

}
