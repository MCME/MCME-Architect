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
package com.mcmiddleearth.architect.specialBlockHandling.specialBlocks;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.architect.PluginData;
import com.mcmiddleearth.architect.specialBlockHandling.SpecialBlockType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Wall;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 *
 * @author Eriol_Eandur
 */
public class SpecialBlockFourDirectionsComplex extends SpecialBlockOrientable {

    protected static final Orientation[] fourFaces = new Orientation[] {
        new Orientation(BlockFace.SOUTH,"South"),
        new Orientation(BlockFace.WEST,"West"),
        new Orientation(BlockFace.NORTH,"North"),
        new Orientation(BlockFace.EAST,"East")
    };

    private final EditData editData[];

    protected SpecialBlockFourDirectionsComplex(String id,
                                                BlockData[] data, EditData editData[]) {
        super(id, data, SpecialBlockType.FOUR_DIRECTIONS_COMPLEX);
        orientations = fourFaces;
        this.editData = editData;
    }

    public static SpecialBlockFourDirectionsComplex loadFromConfig(ConfigurationSection config, String id) {
        BlockData[] data = loadBlockDataFromConfig(config, fourFaces);
        if(data==null) {
            return null;
        }
        EditData[] editData = loadEditDataFromConfig(config, data, fourFaces);
        if(editData==null) {
            return null;
        }
        return new SpecialBlockFourDirectionsComplex(id, data, editData);
    }

    public static EditData[] loadEditDataFromConfig(ConfigurationSection config, BlockData[] data, Orientation[] fourFaces) {
        ConfigurationSection editSection = config.getConfigurationSection("editDataNorth");
        if(editSection==null) {
            return null;
        }
        EditData[] editData = new EditData[4];
        try {
            editData[0] = new EditData(editSection, /*blockDataNorth*/ data[2]);
        } catch(IllegalArgumentException | NullPointerException ex) {
            Logger.getLogger(SpecialBlockFourDirectionsComplex.class.getSimpleName()).warning("Invalid edit data!");
            return null;
        }
        for(int i = 1; i < 4; i++) {
            editData[i] = editData[i-1].rotate(data[(i+2)%4]);
        }
        editSection = config.getConfigurationSection("editDataEast");
        if(editSection!=null) {
            editData[1].readFrom(editSection);
        }
        editSection = config.getConfigurationSection("editDataSouth");
        if(editSection!=null) {
            editData[2].readFrom(editSection);
        }
        editSection = config.getConfigurationSection("editDataWest");
        if(editSection!=null) {
            editData[3].readFrom(editSection);
        }
        return editData;
    }
    
    @Override
    public BlockState getBlockState(Block blockPlace, Block clicked, BlockFace blockFace,
                                    Player player, Location interactionPoint) {
        /*final BlockState state = blockPlace.getState();
        BlockFace face = getBlockFaceFromLoc(playerLoc,false);
        state.setBlockData(getBlockData(face));
        return state;*/
        final BlockState state = blockPlace.getState();
        state.setBlockData(getBlockData(getBlockFace(player.getLocation().getYaw())));
        return state;
    }

    /*@Override
    public Block getBlock(Block clicked, BlockFace blockFace, Player player) {
        if(!player.isSneaking()) {
            return super.getBlock(clicked,blockFace,player);
        } else {
            return getBlockFromLocation(clicked, player.getLocation());
        }
    }*/

    /*private Block getBlockFromLocation(Block clicked, Location loc) {
        Block result;
        if(loc.getPitch()>0) {
            result = clicked.getRelative(BlockFace.UP);
        } else {
            result = clicked.getRelative(BlockFace.DOWN);
        }
        return result = result.getRelative(getBlockFace(loc.getYaw()+180));
    }*/

    @Override
    public boolean isEditOnSneaking() { return true; }

    @Override
    public void placeBlock(final Block blockPlace, final BlockFace blockFace, Block clicked,
                           final Location interactionPoint, final Player player) {
        if(!player.isSneaking()) {
            super.placeBlock(blockPlace, blockFace, clicked, interactionPoint, player);
        } else {
            //Block clicked = blockPlace.getRelative(blockFace.getOppositeFace());
            /*Location loc = player.getLocation().clone();
            loc.setPitch(-loc.getPitch());
            loc.setYaw(loc.getYaw()+180);*/
            //String blockId = SpecialBlockInventoryData.getSpecialBlockDataFromItem(
                //        SpecialBlockInventoryData.getItem(clicked, SpecialBlockInventoryData.rpName(getId()))).getId();
//Logger.getGlobal().info("block place: "+clicked);
//Logger.getGlobal().info("matches: "+matches(clicked));
           //if(getId().equals(SpecialBlockInventoryData.getSpecialBlockDataFromItem(
            //        SpecialBlockInventoryData.getItem(clicked, SpecialBlockInventoryData.rpName(getId()))).getId())) {
            //if(getBlockDatas()[0].getMaterial().equals(clicked.getType())) {
            if(matches(clicked.getBlockData())) {
                /*BlockDataManager manager = new BlockDataManager();
                String searchFor = getBlockFaceFromLoc(player.getLocation(),true).name();*/
                BlockData data = clicked.getBlockData();
                BlockFace editFace = getBlockFace(player.getLocation().getYaw()+180);
                for(int i = 0; i < 4 ; i++) {
//Logger.getGlobal().info("try: "+i);
                    int[] indices = editData[i].getIndicesFor(data);
                    if (indices.length > 0) {
//Logger.getGlobal().info("found");
                        int editIndex = switch (editFace) {
                            case EAST -> 1;
                            case SOUTH -> 2;
                            case WEST -> 3;
                            default -> 0;
                        };
                        if (indices[editIndex] == 0) {
                            indices[editIndex] = 1;
                        } else {
                            indices[editIndex] = 0;
                        }
                        data = editData[i].getBlockData(indices);
                        if (data != null) {
                            /*for(int j = i; j != 0 && j != 4 ; j++) {
                                data = rotateData(data);
                            }
                            if(i != 0 && editData.getIndicesFor(data).length>0 && data instanceof Wall) {
                                ((Wall)data).setUp(!((Wall)data).isUp());
                            }*/
                            BlockData finalData = data;
                            if (PluginData.isAllowedBlock(player, data)) {
                                Logger.getGlobal().info("place: " + finalData.toString());
                                clicked.setBlockData(finalData, false);
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        clicked.setBlockData(finalData, false);
                                    }
                                }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                            }
                        }
                        return;
                    }
                    //data = rotateData(data);
                    //editFace = rotateFace(editFace);
                }
                /*Attribute attrib = manager.getAttribute(data);
                int i = 0;
                if (attrib != null) {
                    int countAttribs = manager.countAttributes(data);
                    while(!attrib.getName().equalsIgnoreCase(searchFor) && i < countAttribs) {
                        manager.nextAttribute(data);
                        attrib = manager.getAttribute(data);
                        i++;
                    }
                    attrib.cycleState();
                    if(PluginData.isAllowedBlock(player, data)) {
                        blockPlace.setBlockData(data, false);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                blockPlace.setBlockData(data, false);
                            }
                        }.runTaskLater(ArchitectPlugin.getPluginInstance(), 1);
                    }
                }*/
            }
        }
    }

    private static BlockData rotateData(BlockData data) {
        if(data instanceof MultipleFacing multi) {
            MultipleFacing result = (MultipleFacing) data.clone();
            result.setFace(BlockFace.NORTH, multi.hasFace(BlockFace.WEST));
            result.setFace(BlockFace.EAST, multi.hasFace(BlockFace.NORTH));
            result.setFace(BlockFace.SOUTH, multi.hasFace(BlockFace.EAST));
            result.setFace(BlockFace.WEST, multi.hasFace(BlockFace.SOUTH));
            return result;
        } else if(data instanceof Wall multi) {
            Wall result = (Wall) data.clone();
            result.setHeight(BlockFace.NORTH, multi.getHeight(BlockFace.WEST));
            result.setHeight(BlockFace.EAST, multi.getHeight(BlockFace.NORTH));
            result.setHeight(BlockFace.SOUTH, multi.getHeight(BlockFace.EAST));
            result.setHeight(BlockFace.WEST, multi.getHeight(BlockFace.SOUTH));
            return result;
        } else if(data instanceof Directional directional) {
            Directional result = (Directional) data.clone();
            switch(directional.getFacing()) {
                case NORTH -> result.setFacing(BlockFace.EAST);
                case EAST -> result.setFacing(BlockFace.SOUTH);
                case SOUTH -> result.setFacing(BlockFace.WEST);
                case WEST -> result.setFacing(BlockFace.NORTH);
            }
            return result;
        }
        return data;
    }

    private BlockFace rotateFace(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            default -> BlockFace.NORTH;
        };
    }

    @Override
    public boolean matches(Block block) {
        return matches(block.getBlockData());
    }

    @Override
    public boolean matches(BlockData blockData) {
        if(super.matches(blockData)) return true;
        for(EditData searchEditData: editData) {
            if(searchEditData.matches(blockData)) return true;
        }
        return false;
    }

    protected EditData[] getEditData() {
        return editData;
    }

    /*private BlockFace getBlockFaceFromLoc(Location loc) {
        BlockFace face;
        if(Math.abs(loc.getPitch())>45) {
            face = BlockFace.UP;
        } else {
            if(loc.getPitch()>0 && !alwaysOpposite) {
                face = getBlockFace(loc.getYaw());
            } else {
                face = getBlockFace(loc.getYaw() + 180);
            }
        }
        return face;
    }*/

    public static class EditData {

        public static final int NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3;
                                                     //N E S W
        private static String[][][][] keys = new String[][][][] {
            {
                {
                    {
                        "",
                        "W"
                    },
                    {
                        "S",
                        "WS"
                    }
                },
                {
                    {
                        "E",
                        "WE"
                    },
                    {
                        "SE",
                        "WSE"
                    }
                }
            },
            {
                {
                    {
                        "N",
                        "NW"
                    },
                    {
                        "NS",
                        "NWS"
                    }
                },
                {
                    {
                        "EN",
                        "ENW"
                    },
                    {
                        "SEN",
                        "NWSE"
                    }
                }
            }
        };

        private final BlockData[][][][] data;

        private EditData(BlockData noConnection) {
            data = new BlockData[2][2][2][2];
            data[0][0][0][0]=noConnection;
        }

        public EditData(ConfigurationSection config, BlockData noConnection) {
            this(noConnection);
            for(int i = 0; i< 2; i++) {
                for(int j = 0; j< 2; j++) {
                    for(int k = 0; k< 2; k++) {
                        for (int l = 0; l < 2; l++) {
                            if(!(i==0 && j==0 && k==0 && l==0)) {
                                String dataString = config.getString(keys[i][j][k][l]);
                                if(dataString != null) {
                                    data[i][j][k][l] = Bukkit
                                            .createBlockData(dataString);
                                } else {
                                    data[i][j][k][l] = null;
                                }
                            }
                        }
                    }
                }
            }
            /*data = new BlockData[][][][] {
                    {
                            {
                                    {
                                            noConnection,
                                            Bukkit.createBlockData(config.getString("W"))
                                    },
                                    {
                                            Bukkit.createBlockData(config.getString("S")),
                                            Bukkit.createBlockData(config.getString("WS"))
                                    }
                            },
                            {
                                    {
                                            Bukkit.createBlockData(config.getString("E")),
                                            Bukkit.createBlockData(config.getString("WE"))
                                    },
                                    {
                                            Bukkit.createBlockData(config.getString("SE")),
                                            Bukkit.createBlockData(config.getString("WSE"))
                                    }
                            }
                    },
                    {
                            {
                                    {
                                            Bukkit.createBlockData(config.getString("N")),
                                            Bukkit.createBlockData(config.getString("NW"))
                                    },
                                    {
                                            Bukkit.createBlockData( config.getString("NS")),
                                            Bukkit.createBlockData(config.getString("NWS"))
                                    }
                            },
                            {
                                    {
                                            Bukkit.createBlockData(config.getString("EN")),
                                            Bukkit.createBlockData(config.getString("ENW"))
                                    },
                                    {
                                            Bukkit.createBlockData(config.getString("SEN")),
                                            Bukkit.createBlockData(config.getString("NWSE"))
                                    }
                            }
                    }
            };*/
        }

        public BlockData getBlockData(boolean north, boolean east, boolean south, boolean west) {
            return data[index(north)][index(east)][index(south)][index(west)];
        }

        public BlockData getBlockData(int[] indices) {
            return data[indices[NORTH]][indices[EAST]][indices[SOUTH]][indices[WEST]];
        }

        public int index(boolean present) {
            return present?1:0;
        }

        /*public boolean isValid() {
            for(int i = 0; i< 1; i++) {
                for(int j = 0; j< 1; j++) {
                    for(int k = 0; k< 1; k++) {
                        for (int l = 0; l < 1; l++) {
                            if(data[i][j][k][l]==null) {
                                return false;
                            }
                            try {
                                Bukkit.createBlockData(data[i][j][k][l]);
                            } catch(IllegalArgumentException ex) {
                                return false;
                            }
                        }
                    }
                }
            }
            return true;
        }*/

        public int[] getIndicesFor(BlockData blockData) {
            for(int i = 0; i< 2; i++) {
                for(int j = 0; j< 2; j++) {
                    for(int k = 0; k< 2; k++) {
                        for (int l = 0; l < 2; l++) {
                            if(blockData.matches(data[i][j][k][l])) {
                                return new int[]{i,j,k,l};
                            }
                        }
                    }
                }
            }
            return new int[0];
        }

        public EditData rotate(BlockData noConnection) {
            EditData result = new EditData(noConnection);
            for(int i = 0; i< 2; i++) {
                for(int j = 0; j< 2; j++) {
                    for(int k = 0; k< 2; k++) {
                        for (int l = 0; l < 2; l++) {
                            result.data[l][i][j][k] = SpecialBlockFourDirectionsComplex
                                      .rotateData(this.data[i][j][k][l]);
                        }
                    }
                }
            }
            return result;
        }

        public void readFrom(ConfigurationSection config) {
            for(int i = 0; i< 2; i++) {
                for(int j = 0; j< 2; j++) {
                    for(int k = 0; k< 2; k++) {
                        for (int l = 0; l < 2; l++) {
                            if(!(i==0 && j==0 && k==0 && l==0)) {
                                String configData = config.getString(keys[i][j][k][l]);
                                if(configData!=null && !Bukkit.createBlockData(configData).getMaterial().equals(Material.AIR)) {
                                    data[i][j][k][l] = Bukkit.createBlockData(configData);
                                }
                            }
                        }
                    }
                }
            }
        }

        public boolean matches(BlockData blockData) {
            for(int i = 0; i< 2; i++) {
                for(int j = 0; j< 2; j++) {
                    for(int k = 0; k< 2; k++) {
                        for (int l = 0; l < 2; l++) {
                            if(!(i==0 && j==0 && k==0 && l==0)) {
                                if(blockData.equals(data[i][j][k][l])) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }
    }



}
