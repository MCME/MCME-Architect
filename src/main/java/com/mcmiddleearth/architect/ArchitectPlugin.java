/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect;

import com.mcmiddleearth.architect.additionalCommands.AbstractArchitectCommand;
import com.mcmiddleearth.architect.additionalCommands.ArchitectCommand;
import com.mcmiddleearth.architect.additionalCommands.FbtCommand;
import com.mcmiddleearth.architect.additionalCommands.RpCommand;
import com.mcmiddleearth.architect.additionalListeners.FbtListener;
import com.mcmiddleearth.architect.additionalListeners.GameMechanicsListener;
import com.mcmiddleearth.architect.additionalListeners.HangingEntityProtectionListener;
import com.mcmiddleearth.architect.additionalListeners.StickBlockBreakListener;
import com.mcmiddleearth.architect.additionalListeners.VoxelBiomeBrushListener;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorCommand;
import com.mcmiddleearth.architect.armorStand.ArmorStandListener;
import com.mcmiddleearth.architect.bannerEditor.BannerEditorCommand;
import com.mcmiddleearth.architect.bannerEditor.BannerListener;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadListener;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mcmiddleearth.architect.customHeadManager.HeadCommand;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsCommand;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsListener;
import com.mcmiddleearth.architect.paintingEditor.PaintingListener;
import com.mcmiddleearth.architect.randomiser.RandomiserCommand;
import com.mcmiddleearth.architect.specialBlockHandling.GetCommand;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockListener;
import com.mcmiddleearth.architect.voxelStencilEditor.SlCommand;
import com.mcmiddleearth.architect.voxelStencilEditor.VvCommand;
import com.mcmiddleearth.architect.weSchematicsViewer.SchListCommand;
import com.mcmiddleearth.util.ProtocolLibUtil;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author Eriol_Eandur
 */
public class ArchitectPlugin extends JavaPlugin {
    
    @Getter
    private static ArchitectPlugin pluginInstance;
    
    @Getter
    private final static List<String> commandList = new ArrayList<>();

    @Override
    public void onEnable() {
        pluginInstance = this;
        ProtocolLibUtil.init(this);
        PluginData.getMessageUtil().setPluginName("Architect");
        PluginData.load();
        CustomHeadManagerData.load();
        
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ArmorStandListener(), this);
        pluginManager.registerEvents(new BannerListener(), this);
        pluginManager.registerEvents(new PaintingListener(), this);
        pluginManager.registerEvents(new GameMechanicsListener(), this);
        pluginManager.registerEvents(new NoPhysicsListener(), this);
        pluginManager.registerEvents(new FbtListener(), this);
        pluginManager.registerEvents(new SpecialBlockListener(), this);
        pluginManager.registerEvents(new VoxelBiomeBrushListener(), this);
        pluginManager.registerEvents(new HangingEntityProtectionListener(), this);
        pluginManager.registerEvents(new CustomHeadListener(), this);
        pluginManager.registerEvents(new StickBlockBreakListener(), this);
//        pluginManager.registerEvents(new AfkListener(), this);
            
        // all CommandExecutors should be subclasses of AbstractArchitectCommand
        // AbstractArchitectCommand methods are used by command /architect help
        setCommandExecutor("armor", new ArmorStandEditorCommand());
        setCommandExecutor("banner", new BannerEditorCommand());
        setCommandExecutor("random", new RandomiserCommand());
        setCommandExecutor("noPhy", new NoPhysicsCommand());
        setCommandExecutor("fbt", new FbtCommand());
        setCommandExecutor("get", new GetCommand());
        setCommandExecutor("sl", new SlCommand());
        setCommandExecutor("vv", new VvCommand());
        setCommandExecutor("schlist",new SchListCommand());
        setCommandExecutor("architect", new ArchitectCommand());
        setCommandExecutor("rp", new RpCommand());
        setCommandExecutor("chead", new HeadCommand());
//        setCommandExecutor("newafkk", new NewAfkCommand());
        
        getLogger().info("MCME-Architect Enabled!");
    }
    
    public void setCommandExecutor(String command, AbstractArchitectCommand executor) {
        getCommand(command).setExecutor(executor);
        commandList.add(command);
    }
    
}
