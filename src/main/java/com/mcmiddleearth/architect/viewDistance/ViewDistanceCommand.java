package com.mcmiddleearth.architect.viewDistance;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.NumericUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ViewDistanceCommand extends AbstractArchitectCommand {

    @Override
    public String getHelpPermission() {
        return Permission.VIEW_DISTANCE.getPermissionNode();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender cs, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player player = (Player) cs;
        if(!PluginData.isModuleEnabled(player.getWorld(), Modules.VIEW_DISTANCE)) {
            PluginData.getMessageUtil().sendErrorMessage(cs,"View distance is not enabled for this world.");
            return true;
        }
        if(!PluginData.hasPermission(cs, Permission.VIEW_DISTANCE)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(args.length == 0 ) {
            PluginData.getMessageUtil().sendErrorMessage(player, "You need to specify a view distance (e.g. '/viewdistance 32' or '/viewdistance default').");
        }
        else if(args[0].equalsIgnoreCase("default")) {
            ViewDistanceManager.unsetViewDistance(player);
            PluginData.getMessageUtil().sendInfoMessage(player,"Setting your client view distance to this world's view distance ("
                     + ViewDistanceManager.getViewDistance(player)+").");
        } else if(NumericUtil.isInt(args[0])) {
            ViewDistanceManager.setViewDistance(player,Math.min(500,NumericUtil.getInt(args[0])));
            PluginData.getMessageUtil().sendInfoMessage(player,"Setting your client view distance to "
                     + ViewDistanceManager.getViewDistance(player));
        } else {
            PluginData.getMessageUtil().sendErrorMessage(cs, "You need to specify a view distance or 'default'");
        }
        return true;
    }

    @Override
    public String getShortDescription() {
        return ": Change your view distance.";
    }

    @Override
    public String getUsageDescription() {
        return " <distance> | default: Sets the view distance Architect tells your client, up to 500, so chunks stay in sight; 'default' goes back to this world's value.";
    }

    @Override
    public String getHelpCommand() {
        return null;
    }

    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+ PluginData.getMessageUtil().STRESSED+"viewdistance command -";
        help = new String[][]{{"Keep your client from unloading chunks."}};
        super.sendHelpMessage(player, page);
    }
}
