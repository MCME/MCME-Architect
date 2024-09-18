/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.serverResoucePack;

import com.google.common.base.Joiner;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.WEUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 *
 * @author Eriol_Eandur
 */
public class RpCommand extends AbstractArchitectCommand {

    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if(args.length>0 && args[0].equalsIgnoreCase("release")
                && PluginData.hasPermission(cs, Permission.RESOURCE_PACK_ADMIN)) {
            if(args.length>3) {
                String title = Joiner.on(" ").join(Arrays.copyOfRange(args, 3, args.length));
                RpReleaseUtil.releaseResourcePack(args[1], args[2], title, (exit, exitCode) -> {
                    Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(), () -> {
                        if (exit && exitCode == 0) {
                            PluginData.getMessageUtil().sendInfoMessage(cs,
                                    "RP release finished. You might want to do /rp server "+args[1]+" "+args[2]);

                        } else {
                            PluginData.getMessageUtil().sendErrorMessage(cs,
                                    "Error while creating RP release! Process terminated=" + exit + " exitCode=" + exitCode);
                        }
                    });
                });
            } else {
                PluginData.getMessageUtil().sendErrorMessage(cs, "Command syntax: /rp release <rpName> <Version> <Title>"
                        +"\nReleasing is not implemented for all resource packs.");
            }
            return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("server")
                && PluginData.hasPermission(cs, Permission.RESOURCE_PACK_ADMIN)) {
            if(args.length>3) {
                RpReleaseUtil.setServerResourcePack(cs, args[1], args[2], args[3], success -> {
                    if (success) {
                        PluginData.getMessageUtil().sendInfoMessage(cs, "Server resource pack " + args[1]
                                + " set to version: " + args[2] + " for MC: "+ args[3]);
                    } else {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "Error while setting server resource pack!");
                    }
                });
            } else {
                PluginData.getMessageUtil().sendErrorMessage(cs, "Command syntax: /rp server <rpName> <rpVersion> <requiredMcVersion>");
            }
            return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("calcsha")
                         && PluginData.hasPermission(cs, Permission.RESOURCE_PACK_ADMIN)) { 
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                return true;
            }
            new BukkitRunnable() {
                @Override
                public void run() {
                    String rpName = RpManager.matchRpName(args[1]);
                    boolean allVersions = args.length > 2 && args[2].equals("all");
                    if(RpManager.refreshSHA(cs, rpName, allVersions)) {
                        PluginData.getMessageUtil().sendInfoMessage(cs, "SHA recalculated for rp: "+rpName);
                    } else {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "Error while recalculating SHA for rp: "+args[1]);
                    }
                }}.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
            return true;
        }
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        if(args.length>0 && (args[0].equals("check"))) {
            RpRegion rpRegion = RpManager.getRegion(((Player)cs).getLocation());
            if(rpRegion == null) {
                PluginData.getMessageUtil().sendInfoMessage(cs, "No server rp defined for your location.");
            } else {
                PluginData.getMessageUtil().sendInfoMessage(cs, "At your current location you should use rp '"
                                          +rpRegion.getRp()+"'.");
            }
            return true;
        }
        if(!PluginData.isModuleEnabled(((Player)cs).getWorld(), Modules.RESOURCE_PACK_SWITCHER)) {
            sendNotActivatedMessage(cs);
            return true;
        }
        if(!PluginData.hasPermission(cs,Permission.RESOURCE_PACK_SWITCH)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        ChatColor ccStressed = PluginData.getMessageUtil().STRESSED;
        ChatColor ccInfo = PluginData.getMessageUtil().INFO;
        if(args.length>0 && (args[0].equalsIgnoreCase("auto")
                          || args[0].equalsIgnoreCase("reset")
                          || args[0].equalsIgnoreCase("client")
                          || args[0].equalsIgnoreCase("variant"))) {
            RpPlayerData data = RpManager.getPlayerData((Player)cs);
            switch (args[0].toLowerCase()) {
                case "auto" -> {
                    if (args.length < 2) {
                        data.setAutoRp(true);
                    } else {
                        data.setAutoRp(!(args[1].equals("off") || args[1].equals("false")));
                    }
                    String on = (data.isAutoRp() ? "on" : "off");
                    PluginData.getMessageUtil().sendInfoMessage(cs, "Auto rp switching set to "
                            + ccStressed + on);
                }
                case "reset" -> {
                    RpRegion rpRegion = RpManager.getRegion(((Player)cs).getLocation());
                    if(rpRegion == null) {
                        PluginData.getMessageUtil().sendInfoMessage(cs, "No server rp defined for your location.");
                    } else {
                        data.setAutoRp(true);
                        RpManager.setRp(rpRegion.getRp(),(Player)cs,true);
                        PluginData.getMessageUtil().sendInfoMessage(cs, "Automatic RP switching enabled. RP switched to '"
                                +rpRegion.getRp()+"'.");
                    }
                    return true;
                }
                case "px" -> {
                    if (args.length < 2) {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                        return true;
                    }
                    if (!NumericUtil.isInt(args[1])) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "You need to specify resolution in px. If your resolution is not available for a RP you'll see default resolution.");
                        return true;
                    }
                    int px = NumericUtil.getInt(args[1]);
                    if (!RpManager.searchRpKey(RpManager.getResolutionKey(px))) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "That resolution is not available, try 16 or 32.");
                        return true;
                    }
                    data.setResolution(px);
                    PluginData.getMessageUtil().sendInfoMessage(cs, "RP resolution set to "
                            + ccStressed + px + "px" + ccInfo + ". If this resolution is not available for a RP you'll see default resolution.");
                }
                case "variant" -> {
                    if (args.length < 2) {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                        return true;
                    }
                    if (args[1].startsWith("xxx_") || !RpManager.searchRpKey(args[1])) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "That variant is not available. Try 'light' or 'dark'.");
                        return true;
                    }
                    if (data.getVariant().equals(args[1])) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "You are already using variant " + args[1]);
                        return true;
                    }
                    data.setVariant(args[1]);
                    RpManager.setRp(RpManager.getRpForUrl(data.getCurrentRpUrl()), (Player) cs, true);
                    PluginData.getMessageUtil().sendInfoMessage(cs, "RP variant set to "
                            + ccStressed + args[1] + ccInfo + ". If this variant is not available for a RP you'll see default variant.");
                }
                case "client" -> {
                    if (args.length < 2) {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                        return true;
                    }
                    if (args[1].startsWith("xxx_") || !RpManager.searchRpKey(args[1])) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "That client type is not available. Try 'vanilla' or 'sodium'.");
                        return true;
                    }
                    if (data.getClient().equals(args[1])) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "You have already set your client type to " + args[1]);
                        return true;
                    }
                    data.setClient(args[1]);
                    RpManager.setRp(RpManager.getRpForUrl(data.getCurrentRpUrl()), (Player) cs, true);
                    PluginData.getMessageUtil().sendInfoMessage(cs, "client type set to "
                            + ccStressed + args[1] + ccInfo + ". If there is no rp for this client type available you'll get the rp for vanilla clients.");
                }
            }
            RpManager.savePlayerData((Player)cs);
            if((data.isAutoRp() || !args[0].equalsIgnoreCase("auto"))
                    && data.getCurrentRegion()!=null) {
                RpManager.setRp(data.getCurrentRegion().getRp(), (Player)cs, false);
            }
            return true;
        }
        if(args.length>0 && (args[0].equalsIgnoreCase("list"))
                && PluginData.hasPermission(cs,Permission.RESOURCE_PACK_ADMIN)) {
            int pageIndex = 0;
            String selection = "";
            if(args.length>1 && (!NumericUtil.isInt(args[1]))) {
                selection = args[1];
                pageIndex = 1;
            }
            int page = 1;
            if(args.length>pageIndex && NumericUtil.isInt(args[pageIndex])) {
                page = NumericUtil.getInt(args[pageIndex]);
            }
            FancyMessage header = new FancyMessage(MessageType.INFO,PluginData.getMessageUtil())
                                        .addSimple("Resource pack areas (click to edit)");
            List<FancyMessage> messages = new ArrayList<>();
            for(String areaName : RpManager.getRegions().keySet()) {
                if(selection.equals("") || areaName.contains(selection)) {
                    FancyMessage message = new FancyMessage(MessageType.INFO_NO_PREFIX, PluginData.getMessageUtil());
                    message.addSimple(ChatColor.AQUA+PluginData.getMessageUtil().getNOPREFIX()+"- ");
                    message.addClickable(ChatColor.BLUE+areaName,"/rp edit "+areaName);
                    message.addSimple(ChatColor.AQUA+".");
                    messages.add(message);
                }
            }
            PluginData.getMessageUtil().sendFancyListMessage((Player)cs, header, messages, "/rp list "+selection, page);
            return true;
        }
        if(args.length>0 && (args[0].equalsIgnoreCase("edit")
                             || args[0].equalsIgnoreCase("create")
                             || args[0].equalsIgnoreCase("remove"))
                         && PluginData.hasPermission(cs, Permission.RESOURCE_PACK_ADMIN)) {
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                return true;
            }
            RpRegion region = RpManager.getRegion(args[1]);
            switch(args[0]) {
                case "edit":
                    if(region==null) {
                        sendNoRegionFoundMessage(cs);
                        return true;
                    }
                    RpManager.getRegionEditConversationFactory().start((Player)cs, region);
                    return true;
                case "create":
                    if(region!=null) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "A rp region with that name already exists.");
                        return true;
                    }
                    Region weRegion = WEUtil.getSelection((Player)cs);
                    if(weRegion==null) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "Please make a WE selection first.");
                        return true;
                    }
                    weRegion = weRegion.clone();
                    if(weRegion instanceof Polygonal2DRegion) {
                        ((Polygonal2DRegion)weRegion).setMaximumY(((Player)cs).getWorld().getMaxHeight());
                        ((Polygonal2DRegion)weRegion).setMinimumY(0);
                    }
                    region = new RpRegion(args[1],weRegion);
                    RpManager.saveRpRegion(region);
                    RpManager.addRegion(region);
                    RpManager.getRegionEditConversationFactory().start((Player)cs, region);
                    return true;
                case "remove":
                    if(region==null) {
                        sendNoRegionFoundMessage(cs);
                        return true;
                    }
                    RpManager.removeRegion(region.getName());
                    return true;
                case "list":
                    
                default: return false;
            }
        }
        if(args.length<1 || args[0].equalsIgnoreCase("help")) {
            int page = 1;
            if(args.length>1 && NumericUtil.isInt(args[1])) {
                page = NumericUtil.getInt(args[1]);
            }
            sendHelpMessage((Player)cs,page);
            return true;
        }
        if(args[0].equalsIgnoreCase("g") || args[0].equalsIgnoreCase("e")) {
            args[0] = "h";
        }
        String rpName = RpManager.matchRpName(args[0]);
        if(rpName.equals("")) {
            sendRPNotFoundMessage(cs);
            return true;
        }
        if(RpManager.getRpUrl(rpName, (Player)cs).equals("")) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Missing url configuration for rp: "
                                                            +ccStressed+rpName);
            return true;
        }
        boolean force = false;
        if(args.length>1 && args[1].equalsIgnoreCase("-force")) {
            force = true;
        }
        if(RpManager.setRp(rpName,(Player)cs,force)) {
            PluginData.getMessageUtil().sendInfoMessage(cs, "Server resource pack set to: "
                                                            +ccStressed+rpName);
        } else {
            PluginData.getMessageUtil().sendErrorMessage(cs, "You are already using rp "
                                                            +ccStressed+rpName);
        }
        /*String urlStr = PluginData.getRpUrl(PluginData.matchRpName(args[0]));//"";
        
        if(urlStr.equals("")) {
            sendRPNotFoundMessage(cs);
            return true;
        }
        new RPSwitcher(urlStr, (Player) cs).start();*/
        return true;
    }

    private void sendRPNotFoundMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Resource pack not found. For more info use /rp help.");
    }

    private void sendNotActivatedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Resouce pack switcher is not activated for this world.");
    }
 
    @Override
    public String getHelpPermission() {
        return Permission.RESOURCE_PACK_SWITCH.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Switches resourcepacks.";
    }

    @Override
    public String getUsageDescription() {
        return " <resourcepack>: Switch to a MCME region resourcepack. Will have an effect only with server textures enabled. \n "
                +ChatColor.WHITE+"Click for details.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/rp help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Resource Pack Switcher -";
        help = new String[][]{         {"/rp r","",": Rohan"},
                                       {"/rp h","",": Human (merged Gondor/Eriador)"},
                                       {"/rp l","",": Lothlorien"},
                                       {"/rp p","",": Paths of the Dead"},
                                       {"/rp d","",": Dwarven (Moria)"},
                                       {"/rp m","",": Mordor"}};
        super.sendHelpMessage(player, page);
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(player, "Enable server textures to use this command: Disconnect > Edit MCME Server > Server Resource Packs: enabled");
    }

    private void sendNoRegionFoundMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "No rp region with that name found.");
    }
    
}
