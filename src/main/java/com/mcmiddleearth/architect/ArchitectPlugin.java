/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.mcmiddleearth.architect.WorldGeneration.WorldGenerationManager;
import com.mcmiddleearth.architect.additionalCommands.*;
import com.mcmiddleearth.architect.additionalListeners.*;
import com.mcmiddleearth.architect.armorStand.ArmorStandEditorCommand;
import com.mcmiddleearth.architect.armorStand.ArmorStandListener;
import com.mcmiddleearth.architect.bannerEditor.BannerEditorCommand;
import com.mcmiddleearth.architect.bannerEditor.BannerListener;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateCommand;
import com.mcmiddleearth.architect.chunkUpdate.ChunkUpdateListener;
import com.mcmiddleearth.architect.copyPaste.*;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadListener;
import com.mcmiddleearth.architect.customHeadManager.CustomHeadManagerData;
import com.mcmiddleearth.architect.customHeadManager.HeadCommand;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsCommand;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsData;
import com.mcmiddleearth.architect.noPhysicsEditor.NoPhysicsListener;
import com.mcmiddleearth.architect.paintingEditor.PaintingListener;
import com.mcmiddleearth.architect.randomiser.RandomiserCommand;
import com.mcmiddleearth.architect.serverResoucePack.*;
import com.mcmiddleearth.architect.signEditor.SignCommand;
import com.mcmiddleearth.architect.signEditor.SignListener;
import com.mcmiddleearth.architect.specialBlockHandling.command.GetCommand;
import com.mcmiddleearth.architect.specialBlockHandling.command.InvCommand;
import com.mcmiddleearth.architect.specialBlockHandling.command.SwitchStickCommand;
import com.mcmiddleearth.architect.specialBlockHandling.customInventories.editor.CustomInventoryEditor;
import com.mcmiddleearth.architect.specialBlockHandling.data.*;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockCommand;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockListener;
import com.mcmiddleearth.architect.specialBlockHandling.itemBlock.ItemBlockManager;
import com.mcmiddleearth.architect.specialBlockHandling.listener.*;
import com.mcmiddleearth.architect.viewDistance.ViewDistanceCommand;
import com.mcmiddleearth.architect.viewDistance.ViewDistanceListener;
import com.mcmiddleearth.architect.viewDistance.ViewDistanceManager;
import com.mcmiddleearth.architect.voxelStencilEditor.SlCommand;
import com.mcmiddleearth.architect.voxelStencilEditor.VvCommand;
import com.mcmiddleearth.architect.weSchematicsViewer.SchListCommand;
import com.mcmiddleearth.pluginutil.developer.Debugable;
import com.mcmiddleearth.pluginutil.developer.DevUtil;
import org.bukkit.Bukkit;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Eriol_Eandur
 */
public class ArchitectPlugin extends JavaPlugin implements Debugable {
    
    private static ArchitectPlugin pluginInstance;
    
    private final static List<String> commandList = new ArrayList<>();

    private BukkitTask rpSwitchTask;

    private DevUtil devUtil;
    
    @Override
    public void onEnable() {
        devUtil = new DevUtil();

        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        pluginInstance = this;
        //ProtocolLibUtil.init(this);
        //DoorListener.addOpenHalfDoorListener();
        PluginData.getMessageUtil().setPluginName("Architect");

        loadData();
        WorldConfig serverConfig = PluginData.getOrCreateWorldConfig("server");

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new AntiKickListener(), this);
        pluginManager.registerEvents(new ArmorStandListener(), this);
        pluginManager.registerEvents(new BannerListener(), this);
        pluginManager.registerEvents(new PaintingListener(), this);
        pluginManager.registerEvents(new GameMechanicsListener(), this);
        pluginManager.registerEvents(new NoPhysicsListener(), this);
        pluginManager.registerEvents(new FbtListener(), this);
        pluginManager.registerEvents(new SpecialBlockListener(), this);
        pluginManager.registerEvents(new FurnaceListener(), this);
        //pluginManager.registerEvents(new TestListener(), this);
        pluginManager.registerEvents(new VoxelBiomeBrushListener(), this);
        pluginManager.registerEvents(new AdditionalProtectionListener(), this);
        pluginManager.registerEvents(new CustomHeadListener(), this);
        pluginManager.registerEvents(new StickBlockBreakListener(), this);
        pluginManager.registerEvents(new BlockCycleListener(), this);
        pluginManager.registerEvents(new BlockPickerListener(), this);
        pluginManager.registerEvents(new LightBlockListener(), this);
        pluginManager.registerEvents(new LecternProtectionListener(), this);
        pluginManager.registerEvents(new SignListener(), this);
        pluginManager.registerEvents(new DoorListener(), this);
        pluginManager.registerEvents(new InventoryListener(), this);
        pluginManager.registerEvents(new RpListener(), this);
        pluginManager.registerEvents(new ChunkUpdateListener(), this);
        pluginManager.registerEvents(new OpItemListener(), this);
        pluginManager.registerEvents(new ClipboardPlayerListener(), this);
        pluginManager.registerEvents(new ItemBlockListener(), this);
        pluginManager.registerEvents(new InventoryProtectionListener(), this);
//        pluginManager.registerEvents(new AfkListener(), this);

        Bukkit.getMessenger().registerIncomingPluginChannel(this, "mcme-modpack-marker:hello", new RpPluginMessageListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", new TestPluginMessageListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "l:fmlhs", new TestPluginMessageListener());
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "wdl:init", new TestPluginMessageListener());

        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(new ViewDistanceListener(this));

        // all CommandExecutors should be subclasses of AbstractArchitectCommand
        // AbstractArchitectCommand methods are used by command /architect help
        setCommandExecutor("armor", new ArmorStandEditorCommand());
        setCommandExecutor("banner", new BannerEditorCommand());
        setCommandExecutor("random", new RandomiserCommand());
        setCommandExecutor("noPhy", new NoPhysicsCommand());
        if(serverConfig.isModuleEnabled(Modules.FULL_BRIGHTNESS, true)) setCommandExecutor("fbt", new FbtCommand());
        setCommandExecutor("get", new GetCommand());
        setCommandExecutor("sl", new SlCommand());
        setCommandExecutor("vv", new VvCommand());
        setCommandExecutor("schlist",new SchListCommand());
        setCommandExecutor("architect", new ArchitectCommand());
        setCommandExecutor("rp", new RpCommand());
        setCommandExecutor("chead", new HeadCommand());
//        setCommandExecutor("itex", new ItemTexCommand());
        setCommandExecutor("inv", new InvCommand());
        setCommandExecutor("itemblock", new ItemBlockCommand());
        setCommandExecutor("sign", new SignCommand());
        setCommandExecutor("parrot", new ParrotCommand());
        setCommandExecutor("chunkupdate", new ChunkUpdateCommand());
        setCommandExecutor("copy", new CopyCommand());
        setCommandExecutor("cut", new CutCommand());
        setCommandExecutor("paste", new PasteCommand());
        setCommandExecutor("rot", new RotateCommand());
        setCommandExecutor("undo", new UndoCommand());
        setCommandExecutor("redo", new RedoCommand());
        setCommandExecutor("flip", new FlipCommand());
        setCommandExecutor("viewdistance", new ViewDistanceCommand());
        setCommandExecutor("weselect", new WeSelectCommand());
        setCommandExecutor("switchstick", new SwitchStickCommand());
        //setCommandExecutor("speed", new SpeedCommand());
//        setCommandExecutor("newafkk", new NewAfkCommand());

        CustomInventoryEditor.init(this);

        rpSwitchTask = new RPSwitchTask().runTaskTimer(this, 500, 20);
        ItemBlockManager.startEntityGlowTask();
        
        
        getLogger().info("MCME-Architect Enabled!");
    }
    
    @Override
    public void onDisable() {
        rpSwitchTask.cancel();
        RpManager.getDbConnector().disconnect();
        ItemBlockManager.stopEntityGlowTask();
    }
    
    public void setCommandExecutor(String command, AbstractArchitectCommand executor) {
        getCommand(command).setExecutor(executor);
        commandList.add(command);
    }
    
    public void loadData() {
        reloadConfig();
        PluginData.load();
        NoPhysicsData.loadExceptionAreas();
        CustomHeadManagerData.load();
        SpecialBlockInventoryData.loadInventories();
        SpecialSavedInventoryData.loadInventories();
        SpecialItemInventoryData.loadInventories();
        SpecialHeadInventoryData.loadInventory();
        GetData.load();
        RpManager.init();
        ItemBlockManager.init();
        ViewDistanceManager.loadViewDistances();
    }

    public static ArchitectPlugin getPluginInstance() {
        return pluginInstance;
    }

    public static List<String> getCommandList() {
        return commandList;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return WorldGenerationManager.getGenerator(worldName, id);
    }

    @Override
    public DevUtil getDevUtil() {
        return devUtil;
    }
}
