package com.mcmiddleearth.architect.entityLogging;

import com.mcmiddleearth.architect.testsupport.FakeMarkerApi;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;

// A stand-in for the dynmap plugin whose marker API is a FakeMarkerApi: it keeps its markers in
// memory, so a test can look at the map through dynmap's own marker interfaces. Every call outside
// the marker API throws.
//
// Load it AFTER Architect, as on the server (Architect is load: STARTUP, dynmap is not):
//     MockBukkit.loadWith(FakeDynmap.class, FakeDynmap.description())
public class FakeDynmap extends JavaPlugin implements DynmapAPI {

    private final FakeMarkerApi markers = new FakeMarkerApi();

    // While set, looking up or creating an area marker throws, as an incompatible dynmap build
    // does: the classes load and the marker set is made, then a marker call fails.
    public void failDrawing(boolean fail) { markers.failDrawing(fail); }

    public static PluginDescriptionFile description() {
        return new PluginDescriptionFile("dynmap", "3.5-fake", FakeDynmap.class.getName());
    }

    // ---- DynmapAPI: only the marker API is modelled ----

    @Override public MarkerAPI getMarkerAPI() { return markers.api(); }
    @Override public boolean markerAPIInitialized() { return true; }

    @Override public int triggerRenderOfVolume(Location l0, Location l1) { throw no(); }
    @Override public void setPlayerVisiblity(Player player, boolean visible) { throw no(); }
    @Override public boolean getPlayerVisbility(Player player) { throw no(); }
    @Override public void postPlayerMessageToWeb(Player player, String message) { throw no(); }
    @Override public void postPlayerJoinQuitToWeb(Player player, boolean isJoin) { throw no(); }
    @Override public String getDynmapVersion() { throw no(); }
    @Override public void assertPlayerInvisibility(Player player, boolean invisible, Plugin plugin) { throw no(); }
    @Override public void assertPlayerVisibility(Player player, boolean visible, Plugin plugin) { throw no(); }
    @Override public boolean sendBroadcastToWeb(String sender, String message) { throw no(); }
    @Override public int triggerRenderOfVolume(String wid, int minx, int miny, int minz, int maxx, int maxy, int maxz) { throw no(); }
    @Override public int triggerRenderOfBlock(String wid, int x, int y, int z) { throw no(); }
    @Override public void setPauseFullRadiusRenders(boolean dopause) { throw no(); }
    @Override public boolean getPauseFullRadiusRenders() { throw no(); }
    @Override public void setPauseUpdateRenders(boolean dopause) { throw no(); }
    @Override public boolean getPauseUpdateRenders() { throw no(); }
    @Override public void setPlayerVisiblity(String player, boolean visible) { throw no(); }
    @Override public boolean getPlayerVisbility(String player) { throw no(); }
    @Override public void assertPlayerInvisibility(String player, boolean invisible, String plugin_id) { throw no(); }
    @Override public void assertPlayerVisibility(String player, boolean visible, String plugin_id) { throw no(); }
    @Override public void postPlayerMessageToWeb(String playerid, String playerdisplay, String message) { throw no(); }
    @Override public void postPlayerJoinQuitToWeb(String playerid, String playerdisplay, boolean isjoin) { throw no(); }
    @Override public String getDynmapCoreVersion() { throw no(); }
    @Override public boolean setDisableChatToWebProcessing(boolean disable) { throw no(); }
    @Override public boolean testIfPlayerVisibleToPlayer(String player, String player_to_see) { throw no(); }
    @Override public boolean testIfPlayerInfoProtected() { throw no(); }
    @Override public void processSignChange(String material, String world, int x, int y, int z, String[] lines, String playerid) { throw no(); }

    private static UnsupportedOperationException no() {
        return new UnsupportedOperationException("FakeDynmap models only the marker API");
    }
}
