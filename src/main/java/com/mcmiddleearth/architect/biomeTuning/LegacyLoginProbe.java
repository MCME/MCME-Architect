package com.mcmiddleearth.architect.biomeTuning;

import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * The debug probe (/biometune debug legacylogin): a do-nothing PlayerLoginEvent listener. Registering it reproduces
 * a server where some plugin still uses the legacy login event, which makes Paper refuse reenterConfiguration() for
 * players who log in while it is registered.
 */
@SuppressWarnings("deprecation")
public final class LegacyLoginProbe implements Listener {

    private final Logger logger;

    public LegacyLoginProbe(Logger logger) {
        this.logger = logger;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        logger.info("biometune: PlayerLoginEvent fired for " + event.getPlayer().getName());
    }
}
