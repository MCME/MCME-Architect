/*
 * Copyright (C) 2019 Eriol_Eandur
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
package com.mcmiddleearth.architect.copyPaste;

import com.mcmiddleearth.architect.ArchitectPlugin;
import com.mcmiddleearth.pluginutil.plotStoring.*;
import com.sk89q.worldedit.regions.CuboidRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.io.*;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Eriol_Eandur
 */
public class Clipboard implements IStoragePlot {
    
    private final Location referencePoint;
    private Location shift;
    
    private byte[] nbtData = null;
    
    protected int rotation; //0, 1, 2 or 3 (90 degree steps)
    
    private boolean[] flip = new boolean[3];

    private final Location lowCorner;
    private final Location highCorner;
    
    private final static String worldMismatch = "You need to be in the same world with your WE selection.";
    
    public Clipboard(Location referencePoint, CuboidRegion weRegion) throws CopyPasteException{
        World world = Bukkit.getWorld(weRegion.getWorld().getName());
        if(world==null || !referencePoint.getWorld().equals(world)) {
            throw new CopyPasteException(worldMismatch);
        }
        this.referencePoint = new Location(referencePoint.getWorld(),
                                           referencePoint.getBlockX(),
                                           referencePoint.getBlockY(),
                                           referencePoint.getBlockZ());
        lowCorner = new Location(world,weRegion.getMinimumPoint().getBlockX(),
                                       weRegion.getMinimumPoint().getBlockY(),
                                       weRegion.getMinimumPoint().getBlockZ());
        highCorner = new Location(world,weRegion.getMaximumPoint().getBlockX(),
                                        weRegion.getMaximumPoint().getBlockY(),
                                        weRegion.getMaximumPoint().getBlockZ());
        shift = lowCorner.clone().subtract(this.referencePoint);
    }
    
    public Clipboard(Location referencePoint, Location lowPoint, Location highPoint) throws CopyPasteException {
        if(referencePoint.getWorld()==null 
                || highPoint.getWorld()==null 
                || lowPoint.getWorld()==null 
                || !referencePoint.getWorld().equals(lowPoint.getWorld())
                || !referencePoint.getWorld().equals(highPoint.getWorld())) {
            throw new CopyPasteException(worldMismatch);
        }
        this.referencePoint = referencePoint.clone();
        lowCorner = new Location (lowPoint.getWorld(), Math.min(lowPoint.getBlockX(), highPoint.getBlockX()),
                                                       Math.min(lowPoint.getBlockY(), highPoint.getBlockY()),
                                                       Math.min(lowPoint.getBlockZ(), highPoint.getBlockZ()));
        highCorner = new Location (lowPoint.getWorld(), Math.max(lowPoint.getBlockX(), highPoint.getBlockX()),
                                                       Math.max(lowPoint.getBlockY(), highPoint.getBlockY()),
                                                       Math.max(lowPoint.getBlockZ(), highPoint.getBlockZ()));
        shift = lowCorner.clone().subtract(this.referencePoint);
    }
    
    public boolean copyToClipboard() {
        StoragePlotSnapshot snapshot = new StoragePlotSnapshot(this);
        try(ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream outStream = new DataOutputStream(
                                         new BufferedOutputStream(
                                         new GZIPOutputStream(
                                         byteOut)))) {
            new MCMEPlotFormat().save(this, outStream, snapshot);
            outStream.flush();
            outStream.close();
            nbtData = byteOut.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(Clipboard.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
    
    public boolean cutToClipboard() {
        if(copyToClipboard()) {
            Collection<Entity> entities = lowCorner.getWorld()
                     .getNearbyEntities(new BoundingBox(lowCorner.getBlockX(),
                                                        lowCorner.getBlockY(),
                                                        lowCorner.getBlockZ(),
                                                        highCorner.getBlockX()+1,
                                                        highCorner.getBlockY()+1,
                                                        highCorner.getBlockZ()+1),
                            new MCMEEntityFilter());
            entities.forEach((entity) -> {
                entity.remove();
            });
            BlockData air = Bukkit.createBlockData(Material.AIR);
            World world = lowCorner.getWorld();
            for(int i = lowCorner.getBlockX(); i <= highCorner.getBlockX();i++) {
                for(int j = lowCorner.getBlockY(); j <= highCorner.getBlockY();j++) {
                    for(int k = lowCorner.getBlockZ(); k <= highCorner.getBlockZ();k++) {
                        world.getBlockAt(i,j,k).setBlockData(air,false);
                    }
                }
            }
            return true;
        }
        return false;
    }
    
    public void rotate(int degree) {
        while(degree<0) {
            degree = degree + 360;
        }
        while(degree>360) {
            degree = degree - 360;
        }
        int steps = degree/90;
        for(int i = 0; i< steps; i++) {
            rotate90();
        }
    }
    
    private void rotate90() {
        Location size = getHighCorner().clone().subtract(getLowCorner());
        shift = new Location(shift.getWorld(),-shift.getBlockZ()-(rotation%2==0?size.getBlockZ():size.getBlockX()),
                                               shift.getBlockY(),
                                               shift.getBlockX());
        rotation = (rotation + 1)%4;
        boolean temp_flip = flip[0];
        flip[0] = flip[2];
        flip[2] = temp_flip;
    }
    
    public void flip(char axis) {
        Location size = getHighCorner().clone().subtract(getLowCorner());
        Location rotatedSize;
        if(rotation%2==0) {
            rotatedSize = size;
        } else {
            rotatedSize = new Location(size.getWorld(),size.getBlockZ(),size.getBlockY(),size.getBlockX());
        }
        switch(axis) {
            case 'x':
                shift = new Location(shift.getWorld(),-shift.getBlockX()-rotatedSize.getBlockX(),
                                                       shift.getBlockY(),
                                                       shift.getBlockZ());
                flip[0] = !flip[0];
                break;
            case 'y':
                shift = new Location(shift.getWorld(), shift.getBlockX(),
                                                      -shift.getBlockY()-rotatedSize.getBlockY(),
                                                       shift.getBlockZ());
                flip[1] = !flip[1];
                break;
            case 'z':
                shift = new Location(shift.getWorld(), shift.getBlockX(),
                                                       shift.getBlockY(),
                                                      -shift.getBlockZ()-rotatedSize.getBlockZ());
                flip[2] = !flip[2];
        }
    }
    
    private void log(String name, Location loc) {
        Logger.getGlobal().info(name+" "+loc.getBlockX()+" "+loc.getBlockY()+" "+loc.getBlockZ());
    }
    
    public IStoragePlot getPastePlot(Location location) throws CopyPasteException {
        Location lowPoint = getPasteLocation(location);
        Location highPoint;
        switch(rotation) {
            case 1:
            case 3:
                highPoint = new Location(lowPoint.getWorld(),
                                         lowPoint.getBlockX()+highCorner.getBlockZ()-lowCorner.getBlockZ(),
                                         lowPoint.getBlockY()+highCorner.getBlockY()-lowCorner.getBlockY(),
                                         lowPoint.getBlockZ()+highCorner.getBlockX()-lowCorner.getBlockX());
                break;
            default:
                highPoint = lowPoint.clone().toVector()
                                .add(highCorner.clone().subtract(lowCorner).toVector())
                                .toLocation(lowPoint.getWorld());
        }
        return new Clipboard(lowPoint,lowPoint,highPoint);
    }
    
    private Location getPasteLocation(Location location) {
        Location paste = location.getBlock().getLocation().toVector()
                                .add(shift.toVector()).toLocation(location.getWorld());
        return paste;
    }
    
    public boolean paste(Location location, boolean withAir, boolean withBiome) {
        Location paste = getPasteLocation(location);
        try(DataInputStream in = new DataInputStream(
                                 new BufferedInputStream(
                                 new GZIPInputStream(
                                 new ByteArrayInputStream(nbtData))))) {
            new MCMEPlotFormat().load(paste, rotation, flip, withAir, withBiome, null, in);
        } catch (IOException | InvalidRestoreDataException ex) {
            Logger.getLogger(Clipboard.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }

    @Override
    public World getWorld() {
        return referencePoint.getWorld();
    }

    @Override
    public boolean isInside(Location lctn) {
        return getLowCorner()!=null && getHighCorner()!=null
                && lctn.getWorld().equals(getWorld())
                && lctn.getBlockX()>=getLowCorner().getBlockX() && lctn.getBlockX()<=getHighCorner().getBlockX()
                && lctn.getBlockY()>=getLowCorner().getBlockY() && lctn.getBlockY()<=getHighCorner().getBlockY()
                && lctn.getBlockZ()>=getLowCorner().getBlockZ() && lctn.getBlockZ()<=getHighCorner().getBlockZ();
    }
    
    public void saveToFile(File file) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if(file.exists()) {
                    file.delete();
                }
                try(DataOutputStream outStream = new DataOutputStream(
                                                 new BufferedOutputStream(
                                                 new FileOutputStream(file)))) {
                    writeLocation(outStream,referencePoint);
                    writeLocation(outStream,shift);
                    writeLocation(outStream,lowCorner);
                    writeLocation(outStream,highCorner);
                    outStream.writeInt(rotation);
                    outStream.writeBoolean(flip[0]);
                    outStream.writeBoolean(flip[1]);
                    outStream.writeBoolean(flip[2]);
                    int i = 0;
                    final int inc = 4048;
                    while(i<nbtData.length) {
                        outStream.write(nbtData,i,Math.min(inc,nbtData.length-i));
                        i+=inc;
                    }
                    outStream.flush();
                } catch (IOException ex) {
                    Logger.getLogger(Clipboard.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }.runTaskAsynchronously(ArchitectPlugin.getPluginInstance());
    }
    
    private void writeLocation(DataOutputStream out, Location loc) throws IOException {
        out.writeInt(loc.getBlockX());
        out.writeInt(loc.getBlockY());
        out.writeInt(loc.getBlockZ());
    }
 
    public Clipboard(File file) throws IOException {
        try(DataInputStream inStream = new DataInputStream(
                                         new BufferedInputStream(
                                         new FileInputStream(file)));
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                ) {
            referencePoint = readLocation(inStream);
            shift = readLocation(inStream);
            lowCorner = readLocation(inStream);
            highCorner = readLocation(inStream);
            rotation = inStream.readInt();
            flip[0]=inStream.readBoolean();
            flip[1]=inStream.readBoolean();
            flip[2]=inStream.readBoolean();
            final int inc = 4048;
            int read = 0;
            byte[] buffer = new byte[inc];
            do {
                read = inStream.read(buffer);
                if(read>0) {
                    byteOut.write(buffer,0,read);
                }
            } while(read>0);
            nbtData = byteOut.toByteArray();
        }
    }
    
    private Location readLocation(DataInputStream in) throws IOException {
        return new Location (Bukkit.getWorlds().get(0),
                             in.readInt(),in.readInt(),in.readInt());
    }

    public Location getReferencePoint() {
        return referencePoint;
    }

    public byte[] getNbtData() {
        return nbtData;
    }

    public int getRotation() {
        return rotation;
    }

    @Override
    public Location getLowCorner() {
        return lowCorner;
    }

    @Override
    public Location getHighCorner() {
        return highCorner;
    }
}
