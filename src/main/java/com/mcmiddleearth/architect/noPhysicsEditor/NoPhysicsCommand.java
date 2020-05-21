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
package com.mcmiddleearth.architect.noPhysicsEditor;

import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.pluginutil.WEUtil;
import com.mcmiddleearth.pluginutil.NumericUtil;
import com.mcmiddleearth.pluginutil.message.FancyMessage;
import com.mcmiddleearth.pluginutil.message.MessageType;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class NoPhysicsCommand extends AbstractArchitectCommand {

    private String inverted = "";
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        if(!PluginData.hasPermission((Player)cs,Permission.NO_PHYSICS_LIST)) {
            PluginData.getMessageUtil().sendNoPermissionError(cs);
            return true;
        }
        if(!PluginData.isModuleEnabled(((Player)cs).getWorld(), Modules.NO_PHYSICS_LIST_ENABLED)) {
            sendNotEnabledErrorMessage(cs);
            return true;
        } else {
            Player p = (Player) cs;
            inverted = (PluginData.isModuleEnabled(((Player)cs).getWorld(), 
                                                          Modules.NO_PHYSICS_LIST_INVERTED)?
                               ChatColor.RED+"ALLOW"+PluginData.getMessageUtil().INFO:
                               ChatColor.RED+"NO"+PluginData.getMessageUtil().INFO);
            if(args.length<2 || args[0].equalsIgnoreCase("help")) {
                int page = 1;
                if(args.length>1 && NumericUtil.isInt(args[1])) {
                    page = NumericUtil.getInt(args[1]);
                }
                sendHelpMessage(p,page);
                return true;
            }
            if(args[0].equalsIgnoreCase("exception")) {
                if(!PluginData.hasPermission((Player)cs,Permission.NO_PHYSICS_LIST_EXCEPT)) {
                    PluginData.getMessageUtil().sendNoPermissionError(cs);
                    return true;
                }
                if(args[1].equalsIgnoreCase("redstone")
                        || args[1].equalsIgnoreCase("water")) {
                    Region region= null;
                    //try {
                        //1.13 removed region = WorldEdit.getInstance().getSession(p.getName()).getRegion();
                        region = WEUtil.getSelection((Player)cs);
                    //} catch (NullPointerException | IncompleteRegionException ex) {}
                    if(region instanceof CuboidRegion) {
                        if(args.length>2) {
                            if(NoPhysicsData.exceptionAreaExists(args[2])) {
                                sendAreaAlreadyExistsMessage(p);
                                return true;
                            }
                            NoPhysicsData.setExceptionArea(args[2], (CuboidRegion) region, args[1]);
                            try {
                                NoPhysicsData.save();
                            } catch (IOException ex) {
                                Logger.getLogger(NoPhysicsCommand.class.getName()).log(Level.SEVERE, null, ex);
                                PluginData.getMessageUtil().sendIOError(p);
                                return true;
                            }
                            sendAreaSetMessage(p);
                        } else {
                            PluginData.getMessageUtil().sendNotEnoughArgumentsError(p);
                        }
                    } else {
                        sendInvalidSelection(p);
                    }
                } else if(args[1].equalsIgnoreCase("delete")) {
                    if(args.length>2) {
                        if(!NoPhysicsData.exceptionAreaExists(args[2])) {
                            sendAreaNotFoundMessage(p);
                            return true;
                        }
                        NoPhysicsData.deleteExceptionArea(args[2]);
                        try {
                            NoPhysicsData.save();
                        } catch (IOException ex) {
                            Logger.getLogger(NoPhysicsCommand.class.getName()).log(Level.SEVERE, null, ex);
                            PluginData.getMessageUtil().sendIOError(p);
                            return true;
                        }
                        sendAreaDeletedMessage(p);
                    } else {
                        PluginData.getMessageUtil().sendNotEnoughArgumentsError(p);
                    }
                } else if(args[1].equalsIgnoreCase("list")) {
                    int page = 1;
                    if(args.length>2 && NumericUtil.isInt(args[2])){
                        page = NumericUtil.getInt(args[2]);
                    }
                    PluginData.getMessageUtil().sendFancyListMessage(p, 
                                new FancyMessage(PluginData.getMessageUtil()).addSimple("Redstone Circuit Areas: "),
                                getExceptionAreaList(),
                                "/nophy exception list", page);
                } else {
                    PluginData.getMessageUtil().sendInvalidSubcommandError(p);
                }
                return true;
            }
            if(!args[1].equals(PluginData.getDefaultKey()) && (Bukkit.getWorld(args[1]) == null)) {
                sendWorldNotFoundMessage((Player)cs);
                return true;
            }
            if(args[0].equalsIgnoreCase("list")) {
                PluginData.getMessageUtil().sendInfoMessage(p, inverted+" physics list for "
                                                + PluginData.getMessageUtil().STRESSED
                                                + args[1]+":");
                List<String> npList = NoPhysicsData.getNoPhysicsListAsStrings(args[1]);
                npList.sort(null);
                for(String line: npList) {                
                    PluginData.getMessageUtil().sendIndentedInfoMessage(p,
                                               PluginData.getMessageUtil().STRESSED+ line);
                }
            } else if(args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove")) {
                if(!PluginData.hasPermission((Player)cs,Permission.NO_PHYSICS_LIST_EDIT)) {
                    PluginData.getMessageUtil().sendNoPermissionError(cs);
                    return true;
                }
                if(args.length<3) {
                    PluginData.getMessageUtil().sendNotEnoughArgumentsError(p);
                    sendHelpMessage(p,1);
                    return true;
                }
                BlockData data = NoPhysicsData.createBlockData(args[2]);
                if(data==null) {
                    sendNoValidMaterial(p);
                    return true;
                }
                boolean materialAdded = false;
                boolean materialRemoved = false;
                if(args[0].equalsIgnoreCase("add")) {
                    if(NoPhysicsData.addNpBlock(args[1],args[2])) {
                        sendMaterialAddedMessage(p);
                    } else {
                        sendMaterialAlreadyNpMessage(p);
                    }
                } else {
                    if(NoPhysicsData.removeNpBlock(args[1],args[2])) {
                        sendMaterialRemovedMessage(p);
                    } else {
                        sendMaterialNotNpMessage(p);
                    }
                }
            } else {
                PluginData.getMessageUtil().sendInvalidSubcommandError(p);
                sendHelpMessage(p,1);
            }
        }
        return true;
    }
    
    private List<FancyMessage> getExceptionAreaList() {
        List<FancyMessage> list = new ArrayList<>();
        for(String name: NoPhysicsData.getExceptionAreas().keySet()) {
            ExceptionArea area = NoPhysicsData.getExceptionAreas().get(name);
            list.add(new FancyMessage(MessageType.INFO_INDENTED, PluginData.getMessageUtil())
                          .addFancy(ChatColor.DARK_AQUA+name+": "+ChatColor.WHITE+area.getX()+", "+area.getY()+", "+area.getZ(),
                                    "/nophy exception delete "+name,
                                    ChatColor.GOLD+"SizeX: "+(area.getDX()+1)+"; "+"SizeY: "+(area.getDY()+1)+"; "+"SizeZ: "+(area.getDZ()+1)));
        }
        return list;
    }
    
    private void sendAreaNotFoundMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Exception area not found.");
    }

    private void sendAreaAlreadyExistsMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Exception already exists, delete or choose another name.");
    }

    private void sendAreaDeletedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Exception area deleted.");
    }

    private void sendAreaSetMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs, "Exception area saved.");
    }

    private void sendNotEnabledErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "NoPhysicsList is not enabled for this world.");
    }

    private void sendNoValidMaterial(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "Not a valid block material ID.");
    }

    private void sendMaterialAddedMessage(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p, "Material added to "+inverted+" physics list.");
    }

    private void sendMaterialAlreadyNpMessage(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "Material already is on "+inverted+PluginData.getMessageUtil().ERROR+" physics list.");
    }

    private void sendMaterialRemovedMessage(Player p) {
        PluginData.getMessageUtil().sendInfoMessage(p, "Material removed from "+inverted+" physics list.");
    }

    private void sendMaterialNotNpMessage(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "Material is not on "+inverted+PluginData.getMessageUtil().ERROR+" physics list.");
    }
    
    private void sendInvalidSelection(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "Make a valid WE selection first.");
    }
    
    private void sendWorldNotFoundMessage(Player p) {
        PluginData.getMessageUtil().sendErrorMessage(p, "You must specify a valid world name or '-default'.");
    }
    
    @Override
    public String getHelpPermission() {
        return Permission.NO_PHYSICS_LIST.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Manages no physics blocks list.";
    }

    @Override
    public String getUsageDescription() {
        return ": Manages lists of blocks which will be affected by game physics. \n "
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/nophy help";
    }

    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Physics Editor -";
        help = new String[][]{{"/noPhy list ","<world>|-default",": Shows no physics list.","You may use '-default' instead of a worldname to show the default no physics lists of all worlds without specific settings."},
                                       {"/noPhy add ","<world>|-default <material>",": Adds a material"," to "+inverted+" physics list. Argument <material> must be a block state descriptor."},
                                       {"/noPhy remove ","<world>|-default <material>",": Removes a material"," from "+inverted+" physics list. Argument <material> must be a block state descriptor."},
                                       {"/noPhy exception set ","<name>",": Creates ", "a new exception area."},
                                       {"/noPhy exception delete "," <name>",": Deletes ", "an exception area."},
                                       {"/noPhy exception list "," [#page]",": Displays a list", " of all exception areas."}};
        super.sendHelpMessage(player, page);
    }


}
