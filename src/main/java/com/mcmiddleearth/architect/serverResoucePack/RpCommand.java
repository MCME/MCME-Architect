/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.serverResoucePack;

import com.google.common.base.Joiner;
import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Log;
import com.mcmiddleearth.architect.LogFileManager;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.util.WorldEditGuard;
import com.mcmiddleearth.pluginutil.WEUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
                            PluginData.getMessageUtil().sendInfoMessage(cs, releaseFinishedHint(args[1], args[2]));

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
            if(args.length>1 && args[1].equalsIgnoreCase("remove")) {
                removeSlot(cs, args);
                return true;
            }
            if(args.length>3) {
                String rpName = matchPack(cs, args[1]);
                if(rpName.isEmpty()) {
                    return true;
                }
                // A release name in place of the version once made a slot that matches no client's version.
                if(!RpManager.isMcVersion(args[3].replace('.','_'))) {
                    PluginData.getMessageUtil().sendErrorMessage(cs, "Unknown Minecraft version: " + args[3]
                            + ". Use one of: " + String.join(", ", mcVersionNames()) + ".");
                    return true;
                }
                RpReleaseUtil.setServerResourcePack(cs, rpName, args[2], args[3], result -> {
                    if (result.error()) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "Error while setting server resource pack! See "
                                + LogFileManager.location(ArchitectPlugin.getPluginInstance()) + ".");
                        return;
                    }
                    result.failed().forEach((variant, reason) -> PluginData.getMessageUtil().sendErrorMessage(cs,
                            "Variant " + variant + " of " + rpName + " was not set for MC " + args[3]
                            + " and keeps its url and sha: " + reason));
                    if (result.failed().isEmpty()) {
                        PluginData.getMessageUtil().sendInfoMessage(cs, "Server resource pack " + args[1]
                                + " set to version: " + args[2] + " for MC: "+ args[3]);
                    } else if (result.done().isEmpty()) {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "Server resource pack " + rpName
                                + " was not changed.");
                    } else {
                        PluginData.getMessageUtil().sendErrorMessage(cs, "Server resource pack " + rpName
                                + " set to version " + args[2] + " for MC " + args[3] + " in "
                                + String.join(", ", result.done()) + " only.");
                    }
                });
            } else {
                PluginData.getMessageUtil().sendErrorMessage(cs, "Command syntax: /rp server <rpName> <rpVersion> <requiredMcVersion>"
                        + " or /rp server remove <rpName> <mcVersion>");
            }
            return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("dropdb")
                && PluginData.hasPermission(cs, Permission.RESOURCE_PACK_ADMIN)) {
            if(args.length<2 || !args[1].equalsIgnoreCase("confirm")) {
                PluginData.getMessageUtil().sendErrorMessage(cs,
                        "Deletes RP database table. Confirm with: /rp dropdb confirm");
                return true;
            }
            PluginData.getMessageUtil().sendInfoMessage(cs, "Deleting RP table...");
            new BukkitRunnable() {
                @Override
                public void run() {
                    boolean success = false;
                    try {
                        success = RpManager.getDbConnector().dropTable();
                    } catch (Exception ex) {
                        Log.error("Failed to drop the RP database table", ex);
                    }
                    final boolean result = success;
                    Bukkit.getScheduler().runTask(ArchitectPlugin.getPluginInstance(), () -> {
                        if (result) {
                            PluginData.getMessageUtil().sendInfoMessage(cs, "RP database table successfully deleted.");
                        } else {
                            PluginData.getMessageUtil().sendErrorMessage(cs,
                                    "Error while deleting RP database table. See "
                                    + LogFileManager.location(ArchitectPlugin.getPluginInstance()) + ".");
                        }
                    });
                }
            }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
            return true;
        }
        if(args.length>0 && args[0].equalsIgnoreCase("calcsha")
                         && PluginData.hasPermission(cs, Permission.RESOURCE_PACK_ADMIN)) { 
            if(args.length<2) {
                PluginData.getMessageUtil().sendNotEnoughArgumentsError(cs);
                return true;
            }
            String rpName = matchPack(cs, args[1]);
            if(rpName.isEmpty()) {
                return true;
            }
            boolean allVersions = args.length > 2 && args[2].equals("all");
            RpManager.refreshSHA(cs, rpName, allVersions, result -> {
                if(result.error()) {
                    PluginData.getMessageUtil().sendErrorMessage(cs, "Error while recalculating SHA for rp: " + rpName
                            + "! See " + LogFileManager.location(ArchitectPlugin.getPluginInstance()) + ".");
                    return;
                }
                result.failed().forEach((slot, reason) -> PluginData.getMessageUtil().sendErrorMessage(cs,
                        "Slot " + slot + " of " + rpName + " keeps its sha: " + reason));
                if(result.failed().isEmpty()) {
                    PluginData.getMessageUtil().sendInfoMessage(cs, "SHA recalculated for rp: "+rpName);
                } else if(result.done().isEmpty()) {
                    PluginData.getMessageUtil().sendErrorMessage(cs, "SHA not recalculated for rp: " + rpName + ".");
                } else {
                    PluginData.getMessageUtil().sendErrorMessage(cs, "SHA recalculated for rp: " + rpName + ", but not for "
                            + (result.failed().size() == 1 ? "the slot" : "the " + result.failed().size() + " slots")
                            + " above.");
                }
            });
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
            if(refusedWhileLoading(cs)) {
                return true;
            }
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
                    if (!WorldEditGuard.require(cs)) return true;
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
        if(refusedWhileLoading(cs)) {
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

    // /rp server remove <rpName> <mcVersion> removes the slot for a version from every variant of a pack: the
    // counterpart of /rp server for a slot that is there, such as one made under a release name.
    private static void removeSlot(CommandSender cs, String[] args) {
        if(args.length<4) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Command syntax: /rp server remove <rpName> <mcVersion>");
            return;
        }
        String rpName = matchPack(cs, args[2]);
        if(rpName.isEmpty()) {
            return;
        }
        if(!RpManager.getSlotVersions(rpName).contains(args[3].replace('.','_'))) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Resource pack " + rpName + " has no slot for " + args[3]
                    + ". Its slots: " + String.join(", ", slotNames(rpName)) + ".");
            return;
        }
        RpManager.SlotResult result = RpManager.removeSlots(rpName, args[3].replace('.','_'));
        if(result.error()) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Error while removing a slot of resource pack " + rpName
                    + "! See " + LogFileManager.location(ArchitectPlugin.getPluginInstance()) + ".");
        } else if(result.done().isEmpty()) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Resource pack " + rpName + " has no slot for " + args[3]
                    + " any more.");
        } else {
            PluginData.getMessageUtil().sendInfoMessage(cs, "Slot " + args[3] + " removed from resource pack " + rpName
                    + ": " + String.join(", ", result.done()) + "."
                    + (RpManager.getRpConfig().isSet(rpName) ? ""
                        : " " + rpName + " had no slots left, so it was removed from config.yml."));
        }
    }

    // The versions a pack has slots for, as an admin types them: 26.2, 26.2release and so on.
    private static List<String> slotNames(String rpName) {
        return RpManager.getSlotVersions(rpName).stream().map(version -> version.replace('_','.')).toList();
    }

    // The pack a name matches; or "", once the admin is told the packs there are.
    private static String matchPack(CommandSender cs, String name) {
        String rpName = RpManager.matchRpName(name);
        if(rpName.isEmpty()) {
            PluginData.getMessageUtil().sendErrorMessage(cs, "Unknown resource pack: " + name
                    + ". Use one of: " + String.join(", ", RpManager.getRpConfig().getKeys(false)) + ".");
        }
        return rpName;
    }

    static String releaseFinishedHint(String rpName, String version) {
        return "RP release finished. You might want to do /rp server "+rpName+" "+version+" <mcVersion>";
    }

    // The Minecraft versions a pack can have a slot for, as an admin types them: 26.2, 1.21.4 and so on.
    private static List<String> mcVersionNames() {
        return RpManager.getMcVersions().stream().map(version -> version.replace('_','.')).toList();
    }

    // /rp server completes its words for resource pack admins: remove or the pack, and then the Minecraft version,
    // or, to remove a slot, the pack and then its slots.
    @Override
    public List<String> onTabComplete(CommandSender cs, Command command, String alias, String[] args) {
        if(args.length < 2 || !args[0].equalsIgnoreCase("server")
                || !PluginData.hasPermission(cs, Permission.RESOURCE_PACK_ADMIN)) {
            return new ArrayList<>();
        }
        boolean remove = args[1].equalsIgnoreCase("remove");
        List<String> packs = List.copyOf(RpManager.getRpConfig().getKeys(false));
        List<String> options = switch(args.length) {
            case 2 -> Stream.concat(Stream.of("remove"), packs.stream()).toList();
            case 3 -> remove ? packs : List.of();
            case 4 -> remove ? slotNames(RpManager.matchRpName(args[2])) : mcVersionNames();
            default -> List.of();
        };
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return new ArrayList<>(options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed))
                .toList());
    }

    // While the player's settings are still being read from the RP database, a change would start from defaults
    // that are not kept, and be lost, and a pack would not be sent: the player is asked to wait a moment.
    private static boolean refusedWhileLoading(CommandSender cs) {
        if(cs instanceof Player player && RpManager.isLoadPending(player)) {
            PluginData.getMessageUtil().sendErrorMessage(cs,
                    "Your resource pack settings are still loading, try again in a moment.");
            return true;
        }
        return false;
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
