/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.randomiser;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.Modules;
import com.mcmiddleearth.architect.Permission;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.blockData.BlockDataManager;
import com.mcmiddleearth.architect.blockData.attributes.Attribute;
import com.mcmiddleearth.architect.blockData.attributes.IntAttribute;
import com.mcmiddleearth.pluginutil.NumericUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 *
 * @author Eriol_Eandur
 */
public class RandomiserCommand extends AbstractArchitectCommand {

    private final Map<OfflinePlayer, RandomiserConfig> configList = new HashMap<>();
    
    private final static Set<String> allowedMaterials = new HashSet<>();
    
    private static File configFile;
    
    private static final String configPathAllowedMaterials = "allowed Materials";
    
    public RandomiserCommand() {
        configFile = new File(ArchitectPlugin.getPluginInstance().getDataFolder(), "randomiser.yml");
        if(configFile.exists()) {
            loadAllowedMaterials();
        }
    }
    
    @Override
    public boolean onCommand(CommandSender cs, Command cmd, String c, String[] args) {
        if (!(cs instanceof Player)) {
            PluginData.getMessageUtil().sendPlayerOnlyCommandError(cs);
            return true;
        }
        Player p = (Player) cs;
        if(!PluginData.isModuleEnabled(p.getWorld(), Modules.RANDOMISER)) {
            sendNotActivatedMessage(cs);
            return true;
        } else {
            RandomiserConfig playerConfig =  getPlayerConfig(p);
            if(args.length>0 && (args[0].equalsIgnoreCase("allow") || args[0].equalsIgnoreCase("deny"))){
                if(!PluginData.hasPermission(p, Permission.RANDOMISER_MATERIALS)){
                    PluginData.getMessageUtil().sendNoPermissionError(cs);
                    return true;
                }
                else {
                    if(args.length<2) {
                        sendMissingArgumentErrorMessage(cs);
                        return true;
                    }
                    else {
                        for(int i = 1; i<args.length;i++) {
                            setAllowed(args[i], args[0].equalsIgnoreCase("allow"));
                        }
                        sendMaterialsInfoMessage(cs);
                        saveAllowedMaterials();
                        return true;
                    }
                }
            }
            if (!PluginData.hasPermission(p,Permission.RANDOMISER_USER)) {
                PluginData.getMessageUtil().sendNoPermissionError(cs);
                return true;
            }
            if(args.length<1) {
                randomise(p.getLocation(), playerConfig);
                PluginData.getMessageUtil().sendInfoMessage(cs, "Randomised your surroundings.");
                return true;
            }
            else if(args[0].equalsIgnoreCase("radius")) {
                if(!checkArgs(cs, args,2)) return true;
                playerConfig.setRadius(getInteger(cs, args[1]));
                sendRadiusSetMessage(cs);
                sendInfoMessage(cs,playerConfig);
                return true;
            }
            else if(args[0].equalsIgnoreCase("range")) {
                if(!checkArgs(cs, args,3)) return true;
                playerConfig.setDataValueRange(getInteger(cs, args[1]),
                                               getInteger(cs, args[2]));
                sendRangeSetMessage(cs);
                sendInfoMessage(cs,playerConfig);
                return true;
            }
            else if(args[0].equalsIgnoreCase("prob")) {
                if(!checkArgs(cs, args,playerConfig.countDataValues()+1)) return true;
                int[] newProbs = new int[playerConfig.countDataValues()];
                for(int i=1; i<args.length;i++) {
                    newProbs[i-1] = getInteger(cs, args[i]);
                    if(newProbs[i-1]<0 || newProbs[i-1]>100) {
                        sendIllegalProbMessage(cs);
                        return true;
                    }
                }
                playerConfig.setProbs(newProbs);
                sendProbsSetMessage(cs);
                sendInfoMessage(cs,playerConfig);
                return true;
            }
            else if(args[0].equalsIgnoreCase("material")) {
                if(!checkArgs(cs, args,2)) return true;
                String[] materials= new String[args.length-1];
                for(int i=1; i<args.length;i++) {
                    materials[i-1] = args[i];
                }
                if(playerConfig.setMaterials(materials)) {
                    sendMaterialsSetMessage(cs);
                }
                else {
                    sendMaterialsErrorMessage(cs);
                }
                sendInfoMessage(cs,playerConfig);
                return true;
            }
            else if(args[0].equalsIgnoreCase("show")) {
                sendInfoMessage(cs, playerConfig);
                return true;
            }
            else if(args[0].equalsIgnoreCase("showAllowed")) {
                sendMaterialsInfoMessage(cs);
                return true;
            }
            else if(args[0].equalsIgnoreCase("help")) {
                int page = 1;
                if(args.length>1 && NumericUtil.isInt(args[1])) {
                    page = NumericUtil.getInt(args[1]);
                }
                sendHelpMessage((Player)cs,page);
                return true;
            }
           else {
                PluginData.getMessageUtil().sendInvalidSubcommandError(cs);
                return true;
            }
        }
    }
    
    private RandomiserConfig getPlayerConfig(OfflinePlayer p) {
        for(OfflinePlayer search: configList.keySet()) {
            if(search.getUniqueId().equals(p.getUniqueId())) {
                return configList.get(search);
            }
        }
        RandomiserConfig newConfig = new RandomiserConfig();
        configList.put(p, newConfig);
        return newConfig;
    }
        
    private void randomise(Location center, RandomiserConfig pConfig) {
       // get attribute for name
       // only int Attributes
       // set value with pConfig.randomDataValue
        BlockDataManager manager = new BlockDataManager();
        Map<Material,IntAttribute> attributes = new HashMap<>();
        for(String material: pConfig.getMaterialsSet()) {
            String[] data = material.split(":");
            attributes.put(Material.matchMaterial(data[0]), 
                                                  (IntAttribute) manager.getAttributeByName(data[1]));
        }
        int radius = pConfig.getRadius();
        for(int i = center.getBlockX()-radius; i<center.getBlockX()+radius; i++) {
            for(int j = center.getBlockY()-radius; j<center.getBlockY()+radius; j++) {
                for(int k = center.getBlockZ()-radius; k<center.getBlockZ()+radius;k++) {
                    Location loc = new Location(center.getWorld(),i,j,k);
                    if(center.distance(loc)<radius) {
                        Block block = loc.getBlock();
                        IntAttribute attrib = attributes.get(block.getType());
                        if(attrib!=null) {
                            BlockData data = block.getBlockData();
                            attrib.setBlockData(data);
                            attrib.setState(pConfig.randomDataValue());
                            block.setBlockData(data, false);
                        }
                    }
                }
            }
        }
    }
    
    private int getInteger(CommandSender cs, String str) {
        try {
            return Integer.parseInt(str);
        }
        catch(NumberFormatException e) {
            sendNotANumberErrorMessage(cs);
            return -1;
        }
    }
    
    private boolean checkArgs(CommandSender cs, String[] args, int count) {
        if(args.length<count) {
            sendMissingArgumentErrorMessage(cs);
            return false;
        }
        return true;
    }
    
    public static boolean isAllowed(String mat) {
        return allowedMaterials.contains(mat);
    }
    
    private void setAllowed(String name, boolean allow) {
        String[] data = name.split(":");
        Material material = Material.matchMaterial(data[0]);
        if(material != null && data.length>1) {
            Attribute attrib = new BlockDataManager().getAttributeByName(data[1]);
            if(attrib instanceof IntAttribute) {
                if(allow) {
                    allowedMaterials.add(name);
                }
                else {
                    allowedMaterials.remove(name);
                }
            }
        }
    }
    
    public void saveAllowedMaterials() {
        FileConfiguration config = new YamlConfiguration();
        /*List<String> materialNames = new ArrayList<>();
        for(Material mat : allowedMaterials) {
            materialNames.add(mat.name());
        }*/
        config.set(configPathAllowedMaterials, allowedMaterials);
        try {
            config.save(configFile);
        } catch (IOException ex) {
            Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public final void loadAllowedMaterials() {
        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(configFile);
        } catch (IOException | InvalidConfigurationException ex) {
            Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        List<String> materialNames = config.getStringList(configPathAllowedMaterials);
        allowedMaterials.clear();
        for(String name : materialNames) {
            setAllowed(name,true);
        }
    }
    
    private void sendMissingArgumentErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs,"You're missing arguments for this command.");
    }

    private void sendNotANumberErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs,"Not a number.");
   }

    private void sendInfoMessage(CommandSender cs, RandomiserConfig playerConfig) {
        PluginData.getMessageUtil().sendInfoMessage(cs,"randomiser tool configuration:");
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"Radius: "+PluginData.getMessageUtil().STRESSED+playerConfig.getRadius());
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"Material: "+PluginData.getMessageUtil().STRESSED+playerConfig.getMaterials());
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"DataValueRange: "+PluginData.getMessageUtil().STRESSED+playerConfig.getMinValue()+" "
                                         +playerConfig.getMaxValue());
        PluginData.getMessageUtil().sendNoPrefixInfoMessage(cs,"Probabilities: "+PluginData.getMessageUtil().STRESSED+playerConfig.getProbs());
    }
    
    private void sendMaterialsInfoMessage(CommandSender cs) {
        String matList = "";
        for(String material: allowedMaterials) {
            matList = matList+material+"\n";
        }
        PluginData.getMessageUtil().sendInfoMessage(cs,"Allowed materials: "+PluginData.getMessageUtil().STRESSED+ matList);
    }

    private void sendRadiusSetMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs,"Radius set (max is 150).");
    }

    private void sendProbsSetMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs,"Probabilities set.");
    }

    private void sendRangeSetMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs,"Range of data value set.");
    }

    private void sendMaterialsSetMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendInfoMessage(cs,"Materials set.");
    }

    private void sendMaterialsErrorMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs,"Some materials were not found or are not allowed to be randomised.");
    }

    private void sendNotActivatedMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Field randomizer is not enabled for this world.");
    }

    private void sendIllegalProbMessage(CommandSender cs) {
        PluginData.getMessageUtil().sendErrorMessage(cs, "Probability values must be between 0 and 100.");
    }

    @Override
    public String getHelpPermission() {
        return Permission.RANDOMISER_USER.getPermissionNode();
    }

    @Override
    public String getShortDescription() {
        return ": Block data value randomiser.";
    }

    @Override
    public String getUsageDescription() {
        return ": Randomises data values of blocks. Very useful for crops and other fields. \n"
                +ChatColor.WHITE+"Click for detailed help.";
    }
    
    @Override
    public String getHelpCommand() {
        return "/random help";
    }
    
    @Override
    protected void sendHelpMessage(Player player, int page) {
        helpHeader = "Help for "+PluginData.getMessageUtil().STRESSED+"Block Data Value Randomiser -";
        help = new String[][]{{"/random","",": Randomises your surroundings"," with current settings."},
                                       {"/random radius ","<radius>",": Sets affected radius."},
                                       {"/random range ","<min> <max>",": Sets placed data values",". Previously set probabilties are cleared. All data values will be used in equal amounts."},
                                       {"/random material ","<material> [material] ... ",": Sets affected materials",". You may use material names or block IDs. Blocks with all data values will be affected."},
                                       {"/random prob ","<prob> [prob] ... ",": Sets probabilities",". \n If more arguments for probabilities are specified than used data values surplus arguments are ignored. \n If too few arguments are specified remaining probabilities will be zero. \n If the sum of given probabilities is not 100% last probability will be adjusted."},
                                       {"/random show","",": Shows settings."},
                                       {"/random showAllowed","",": Shows allowed materials."},
                                       {"/random allow "," <material> [material] ... ",": Allowes a material"," to be randomised. Don't allow stairs ;)"},
                                       {"/random deny"," <material> [material] ... ",": Denies a material", " to be randomised."}};
        super.sendHelpMessage(player, page);
    }
    
   

}
