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
package com.mcmiddleearth.architect.voxelStencilEditor;

import com.mcmiddleearth.architect.ArchitectPlugin;
import java.io.File;

/**
 *
 * @author Eriol_Eandur
 */
public class VoxelConstants {
 
    public static final File VOXEL_DIR = new File(ArchitectPlugin.getPluginInstance()
                                                 .getDataFolder().getParent() + "/VoxelSniper");

    public static final File STENCILS_DIR = new File(VOXEL_DIR,"/stencils/");
    
    public static final File STENCIL_LISTS_DIR = new File(VOXEL_DIR,"/stencilLists/");
    
    public static final String STENCIL_EXT = "vstencil";
    
    public static final String STENCIL_LIST_EXT = "txt";
    
}
