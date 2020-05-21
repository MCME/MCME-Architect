/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mcmiddleearth.architect.blockData;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.blockData.attributes.*;
import com.mcmiddleearth.pluginutil.LegacyMaterialUtil;
import com.mcmiddleearth.util.DevUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.*;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.block.data.type.Tripwire;
import org.bukkit.block.data.type.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class BlockDataManager {
    
    private int currentAttribute=0;
    
    private final List<Attribute> attributes = new ArrayList<>();
    
    private static final Map<LegacyBlockData,BlockData> blockIdDataMapping = new HashMap<>();
    
    public BlockDataManager() {
        attributes.add(new BooleanAttribute("Waterlogged",Waterlogged.class));

        attributes.add(new BooleanAttribute("Inverted",DaylightDetector.class));
        attributes.add(new InWallAttribute("In_Wall",Gate.class));
        attributes.add(new BooleanAttribute("Persistent",Leaves.class));
        attributes.add(new BooleanAttribute("Drag",BubbleColumn.class));
        attributes.add(new BooleanAttribute("Conditional",CommandBlock.class));
        attributes.add(new BooleanAttribute("Triggered",Dispenser.class));
        attributes.add(new BooleanAttribute("Enabled",Hopper.class));
        attributes.add(new BooleanAttribute("Extended",Piston.class));
        attributes.add(new BooleanAttribute("Locked",Repeater.class));
        attributes.add(new BooleanAttribute("Unstable",TNT.class));
        attributes.add(new BooleanAttribute("Disarmed",Tripwire.class));
        attributes.add(new SetAttribute("Part",Bed.class,Bed.Part.class));
        attributes.add(new BrewingStandAttribute("Bottle"));
        attributes.add(new IntAttribute("Bites",Cake.class));
        attributes.add(new SetAttribute("Type",Chest.class,Chest.Type.class));
        attributes.add(new SetAttribute("Mode",Comparator.class,Comparator.Mode.class));
        attributes.add(new BooleanAttribute("Eye",EndPortalFrame.class,"hasEye"));
        attributes.add(new IntAttribute("Moisture",Farmland.class));
        attributes.add(new IntAttribute("Distance",Leaves.class,1,7));
        attributes.add(new IntAttribute("Delay",Repeater.class));
        attributes.add(new IntAttribute("Stage",Sapling.class));
        attributes.add(new IntAttribute("Pickles",SeaPickle.class));
        attributes.add(new SetAttribute("Type",Slab.class,Slab.Type.class));
        attributes.add(new IntAttribute("Layers",Snow.class));
        attributes.add(new SetAttribute("Mode",StructureBlock.class,StructureBlock.Mode.class));
        attributes.add(new SetAttribute("Face",Switch.class,Switch.Face.class));
        attributes.add(new SetAttribute("Type",TechnicalPiston.class,TechnicalPiston.Type.class));
        attributes.add(new BooleanAttribute("Short",PistonHead.class));
        attributes.add(new IntAttribute("Eggs",TurtleEgg.class));
        attributes.add(new IntAttribute("Hatch",TurtleEgg.class));
        
        attributes.add(new BooleanAttribute("Powered",Powerable.class));
        attributes.add(new SetAttribute("Half",Bisected.class,Bisected.Half.class));
        attributes.add(new SetAttribute("Hinge",Door.class,Door.Hinge.class));
        attributes.add(new RedstoneWireAttribute("Face"));
        attributes.add(new SetAttribute("Instrument",NoteBlock.class,Instrument.class));
        attributes.add(new NoteAttribute("Note"));
        attributes.add(new SetAttribute("Shape",Stairs.class,Stairs.Shape.class));
        attributes.add(new BooleanAttribute("Open",Openable.class));
        attributes.add(new BooleanAttribute("Attached",Attachable.class));
        attributes.add(new BooleanAttribute("Lit",Lightable.class));
        attributes.add(new BooleanAttribute("Snowy",Snowable.class));
        attributes.add(new SubsetAttribute("Shape",Rail.class,Rail.Shape.class,"getShapes"));
        attributes.add(new MultiFaceAttribute("Face",MultipleFacing.class));
        attributes.add(new SubsetAttribute("Axis",Orientable.class,Axis.class,"getAxes"));
        attributes.add(new SubsetAttribute("Facing",Directional.class,BlockFace.class,"getFaces"));
        attributes.add(new IntAttribute("Age",Ageable.class));
        attributes.add(new IntAttribute("Power",AnaloguePowerable.class));
        attributes.add(new IntAttribute("Level",Levelled.class));
        attributes.add(new RotatableAttribute("Rotation"));
        
        //1.13 - 1.4 additions
        attributes.add(new SetAttribute("Leaves", Bamboo.class, Bamboo.Leaves.class));
        attributes.add(new SetAttribute("Attachment", Bell.class, Bell.Attachment.class));
        attributes.add(new BooleanAttribute("SignalFire", Campfire.class));
        attributes.add(new BooleanAttribute("Hanging", Lantern.class));
        attributes.add(new BooleanAttribute("Bottom", Scaffolding.class));
        attributes.add(new IntAttribute("Distance", Scaffolding.class));

        
    }
    
    public Attribute getAttribute(BlockData data) {
        int sum = -1;
        Attribute last = null;
        for(Attribute search: attributes) {
            if(search.isInstance(data)) {
                search.setBlockData(data);
                last = search;
                int count = search.countSubAttributes();
                if(sum+count>=currentAttribute) {
                    search.setCurrentSubAttribute(currentAttribute-sum-1);
                    return search;
                } else {
                    sum+=count;
                }
            }
        }
        return last;
    }
    
    public int countAttributes(BlockData data) {
        int result = 0;
        for(Attribute search: attributes) {
            if(search.isInstance(data)) {
                search.setBlockData(data);
                result+=search.countSubAttributes();
            }
        }
        return result;
    }
    
    public int countStates(BlockData data) {
        int result = 1;
        for(Attribute search: attributes) {
            if(search.isInstance(data)) {
                search.setBlockData(data);
                int attribs = search.countSubAttributes();
                int countThisAttribute=1;
                for(int i=0;i<attribs;i++) {
                    search.setCurrentSubAttribute(i);
                    countThisAttribute*=search.countStates();
                }
                result*=countThisAttribute;
            }
        }
        return result;
        
    }
    
    public void nextAttribute(BlockData data) {
        int maxIndex = countAttributes(data)-1;
        currentAttribute++;
        if(currentAttribute > maxIndex) {
            currentAttribute = 0;
        }
    }
    
    public List<String> getBlockInfo(BlockData data, byte rawData) {
        List<String> results = new ArrayList<>();
        if(rawData>-1) {
            Material legacy = LegacyMaterialUtil.getLegacyMaterial(data.getMaterial());
            String legacyInfo = (legacy!=null?
                                 " "+ChatColor.RED+"old("+legacy.getId()+":"+rawData+")":"");
            results.add("Material: "+ChatColor.GREEN+data.getMaterial().name()
                                    +/*1.14 removed " ("+data.getMaterial().getId()+":"+rawData+")"+*/legacyInfo);
        } else {
            results.add(data.getMaterial().name());
        }
        for(Attribute search: attributes) {
            if(search.isInstance(data)) {
                search.setBlockData(data);
                for(int i=0; i<search.countSubAttributes();i++) {
                    search.setCurrentSubAttribute(i);
                    if(rawData>-1) {
                        results.add(search.getName()+": "+ChatColor.GREEN+search.getState());
                    } else {
                        results.add(search.getName()+":"+search.getState());
                    }
                }
            }
        }
        return results;
    }
    
    public static BlockData getBlockData(int id, byte rawData) {
        return blockIdDataMapping.get(new LegacyBlockData(id,rawData));
    }
    
    public static LegacyBlockData getLegacyBlockData(BlockData data) {
        World world = Bukkit.getWorlds().get(0);
        if(world==null) return new LegacyBlockData(0,(byte)0);
        Block block = world.getBlockAt(0, 2, 0);
        BlockState state = block.getState();
        return new LegacyBlockData(state.getType().getId(),state.getRawData());
    }
    
    public void createBlockIdDataMapping() {
        Logger.getLogger(this.getClass().getName()).info("Start creating block id data mappings.");
        new BukkitRunnable(){
            int retries = 20;
            @Override
            public void run() {
                World world = Bukkit.getWorlds().get(0);
                if(world !=null) {
                    Block block = world.getBlockAt(0, 2, 0);
                    BlockState state = block.getState();
                    for(Material material: Material.values()) {
                        Material legacy = LegacyMaterialUtil.getLegacyMaterial(material);
                        if(legacy!=null) {
                            List<BlockData> dataList = collectBlockStates(material);
                            for(BlockData data: dataList) {
                                state.setBlockData(data);
                                LegacyBlockData legacyData = new LegacyBlockData(legacy.getId(),
                                                                           state.getRawData());
                                if(!blockIdDataMapping.containsKey(legacyData)) {
                                    blockIdDataMapping.put(legacyData,
                                                           data.clone());
                                }
                            }
                        }
                    }  
                    cancel();
                } else {
                    if(retries>0) {
                        retries--;
                        Logger.getLogger(BlockDataManager.class.getName()).info("World not found, retrying in 10 seconds.");
                    } else {
                        cancel();
                    }
                }
            }
        }.runTaskTimer(ArchitectPlugin.getPluginInstance(), 0, 200);
        Logger.getLogger(this.getClass().getName()).info("Finished creating block id data mappings.");
    }
    
    private List<BlockData> collectBlockStates(Material mat) {
        currentAttribute = 0;
        List<BlockData> result = new ArrayList<>();
        BlockData data = mat.createBlockData();
        recursiveCollectBlockStates(mat, data, result);
        return result;
    }
    
    private void recursiveCollectBlockStates(Material mat, BlockData blockData, List<BlockData> result) {
        BlockData thisData = blockData.clone();
        Attribute attrib = getAttribute(thisData);
        if(attrib==null) {
            result.add(thisData);
            return;
        }
        int countAttributes = countAttributes(thisData);
        int attributeInternalIndex = attrib.getCurrentSubAttribute();
        int thisIndex = currentAttribute;
        int countStates = attrib.countStates();
        for(int i=0; i<countStates;i++) {
            if(thisIndex < countAttributes-1) {
                currentAttribute = thisIndex + 1;
                recursiveCollectBlockStates(mat, thisData, result);
                attrib.setBlockData(thisData);
                attrib.setCurrentSubAttribute(attributeInternalIndex);
            } else {
                result.add(thisData.clone());
            }
            attrib.cycleState();
        }
    }
    
    private void createBlockStatesTree(Material mat, BlockData blockData, List result) {
        BlockData thisData = blockData.clone();
        Attribute attrib = getAttribute(thisData);
        if(attrib==null) {
            result.add(thisData);
            return;
        }
        int countAttributes = countAttributes(thisData);
        int attributeInternalIndex = attrib.getCurrentSubAttribute();
        int thisIndex = currentAttribute;
        int countStates = attrib.countStates();
        for(int i=0; i<countStates;i++) {
            if(thisIndex < countAttributes-1) {
                List next = new ArrayList();
                result.add(next);
                currentAttribute = thisIndex + 1;
                createBlockStatesTree(mat, thisData, next);
                attrib.setBlockData(thisData);
                attrib.setCurrentSubAttribute(attributeInternalIndex);
            } else {
                result.add(thisData.clone());
            }
            attrib.cycleState();
        }
    }
    
    public void saveToConfig(BlockData data, ConfigurationSection config) {
        for(Attribute search: attributes) {
            if(search.isInstance(data)) {
                search.setBlockData(data);
                for(int i=0; i<search.countSubAttributes();i++) {
                    search.setCurrentSubAttribute(i);
                    search.saveToConfig(config);
                }
            }
        }
    }
    
    public void loadFromConfig(BlockData data, ConfigurationSection config) {
        for(Attribute search: attributes) {
            if(search.isInstance(data)) {
                search.setBlockData(data);
                for(int i=0; i<search.countSubAttributes();i++) {
                    search.setCurrentSubAttribute(i);
                    search.loadFromConfig(config);
                }
            }
        }
    }
    
    public synchronized static void placeAllBlocksStates(final Player player, 
            final Block block, final boolean prepare, final boolean fileOutput) {
        try {
            final BufferedWriter writer = (fileOutput?new BufferedWriter(new FileWriter(new File("blockList.txt"))):null);
            List<Material> materials = new ArrayList<>();
            for(Material mat: Material.values()) {
                if(mat.isBlock() ) {
                    materials.add(mat);
                } else {
                    DevUtil.log("skipped: "+mat.name());
                }
            }
            Collections.sort(materials, new MaterialComparator());
            List<Material> sortedMaterials = materials;
            new BukkitRunnable() {

                private int y, x, z;
                private int matCounter = -1;
                private int stateCounter = 0;
                private BlockDataManager attributeManager = new BlockDataManager();
                private boolean rowStarted = false;
                private List cachedStatesTree = new ArrayList();

                @Override
                public synchronized void run() {
                    if(sortedMaterials.isEmpty()) {
                        cancel();
                        return;
                    }
                    if(matCounter==-1) {
                        y = block.getY();
                        z = block.getZ();
                    }
                    Material mat;
                    List blockStateTree;
                    int newStates;
                    do {
                        matCounter++;
                        mat = sortedMaterials.get(matCounter);
                        BlockData blockData = mat.createBlockData();
                        blockStateTree = new ArrayList();
                        attributeManager.setCurrentAttribute(0);
                        attributeManager.createBlockStatesTree(mat, blockData, blockStateTree);
                        cachedStatesTree.add(blockStateTree);
                        newStates = countStatesInBlockStatesTree(cachedStatesTree);
                    } while(countStatesInBlockStatesTree(blockStateTree)<2
                            && matCounter<sortedMaterials.size()-1
                            && newStates<=16 
                            && MaterialComparator.isSimilar(mat, sortedMaterials.get(matCounter+1))
                            && isSingleState(sortedMaterials.get(matCounter+1)));
                    stateCounter += newStates;
                    Logger.getGlobal().log(Level.INFO, 
                                       "Material: {0} placing {1} blockstates, total blockstates: {2}", 
                                       new Object[]{mat.name(), newStates, stateCounter});
                    x = block.getX();
                    rowStarted = false;
                    placeBlockStates(cachedStatesTree,writer,true);
                    cachedStatesTree = new ArrayList();
                    if(matCounter==sortedMaterials.size()-1) {
                        cancel();
                        try {
                            if(writer!=null)
                                writer.close();
                        } catch (IOException ex) {
                            Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        Logger.getGlobal().log(Level.INFO,"Placed {0} blockstates in total.",stateCounter);
                    }
                }
                
                
                private void placeBlockStates(List blockStateTree, BufferedWriter writer, boolean endWithNewRow) {
                    boolean placeInOneRow = countStatesInBlockStatesTree(blockStateTree)<=32;
                    for(Object entry: blockStateTree) {
                        if(entry instanceof List) {
                            placeBlockStates((List)entry, writer, false);
                            if(!placeInOneRow && rowStarted) {
                                x = block.getX();
                                z += 2;
                                rowStarted = false;
                            }
                            if(!placeInOneRow) {
                                endWithNewRow = false;
                            }
                        } else {
                            if(writer == null) {
                                if(prepare) {
                                    prepare(block.getWorld(),x,y,z);
                                }
                                block.getWorld().getBlockAt(x+1, y+1, z+1).setBlockData((BlockData)entry,false);
                                x+=2;
                                rowStarted = true;
                            } else {
                                List<String> lines = attributeManager.getBlockInfo((BlockData)entry, (byte) -1);
                                try {
                                    writer.write(lines.get(0)+",");
                                    for(int i = 1; i<lines.size();i++) {
                                        writer.write(lines.get(i)+",");
                                    }
                                    writer.newLine();
                                } catch (IOException ex) {
                                    Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        }
                    }
                    if(endWithNewRow || (!placeInOneRow && rowStarted)) {// || openRow) {
                        x = block.getX();
                        z += 2;
                        rowStarted = false;
                    }
                }

                private boolean isSingleState(Material mat) {
                    List states = new ArrayList();
                    BlockData blockData = mat.createBlockData();
                    BlockDataManager manager = new BlockDataManager();
                    manager.createBlockStatesTree(mat, blockData, states);
                    return countStatesInBlockStatesTree(states)<2;
                }
                
                private void prepare(World world, int x, int y, int z) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(Material.GRASS_BLOCK, false);
                    for(int i=y+1; i<y+20; i++) {
                        block = world.getBlockAt(x, i, z);
                        block.setType(Material.AIR, false);
                    }
                }

                private int countStatesInBlockStatesTree(List tree) {
                    int result = 0;
                    for(Object entry: tree) {
                        if(entry instanceof List) {
                            result = result + countStatesInBlockStatesTree((List) entry);
                        } else {
                            result++;
                        }
                    }
                    return result;
                }
            }.runTaskTimer(ArchitectPlugin.getPluginInstance(), 1, 1);
        } catch (IOException ex) {
            Logger.getLogger(ArchitectPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public Attribute getAttributeByName(String name) {
        for(Attribute search: attributes) {
            if(search.getName().equalsIgnoreCase(name)) {
                return search;
            }
        }
        return null;
    }

    public int getCurrentAttribute() {
        return currentAttribute;
    }

    public void setCurrentAttribute(int currentAttribute) {
        this.currentAttribute = currentAttribute;
    }
}
