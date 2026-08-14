package org.yudev.airtillery;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class TntExplosionListener implements Listener {
    private final ArtilleryPlugin plugin;

    public TntExplosionListener(ArtilleryPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) event.getEntity();

            if (tnt.hasMetadata("artillery_tnt")) {
                tnt.setFuseTicks(0);
                event.setCancelled(true);
                Location location = tnt.getLocation();
                location.getWorld().spawnParticle(
                        Particle.EXPLOSION_LARGE,
                        location,
                        5, 0.5, 0.5, 0.5, 0
                );
            }
        }
    }

    @EventHandler
    public void onTntExplode(EntityExplodeEvent event) {
        if (event.getEntity() instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) event.getEntity();

            if (tnt.hasMetadata("artillery_tnt")) {
                if (tnt.getVelocity().lengthSquared() < 0.01) {

                    Location location = tnt.getLocation();
                    location.getWorld().spawnParticle(
                            Particle.EXPLOSION_HUGE,
                            location,
                            1, 0, 0, 0, 0
                    );
                }
            }
        }
    }
}