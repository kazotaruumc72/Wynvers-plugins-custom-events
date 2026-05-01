package com.wynvers.customevents.nexo.enderjammer;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks active Ender Pearl Jammers by furniture entity UUID.
 *
 * <p>Pure state. No event listening.
 */
public class EnderJammerManager {

    public static final class ActiveJammer {
        public final UUID entityId;
        public final Location center;
        public final EnderJammerMechanic mechanic;

        public ActiveJammer(UUID entityId, Location center, EnderJammerMechanic mechanic) {
            this.entityId = entityId;
            this.center = center;
            this.mechanic = mechanic;
        }

        public boolean contains(Location loc) {
            World jw = center.getWorld();
            World pw = loc.getWorld();
            if (jw == null || pw == null || !jw.getUID().equals(pw.getUID())) return false;
            int r = mechanic.radius();
            double dx = loc.getX() - (center.getBlockX() + 0.5);
            double dy = loc.getY() - (center.getBlockY() + 0.5);
            double dz = loc.getZ() - (center.getBlockZ() + 0.5);
            return Math.abs(dx) <= r && Math.abs(dy) <= r && Math.abs(dz) <= r;
        }
    }

    private final Map<UUID, ActiveJammer> byEntityId = new HashMap<>();

    public void register(ActiveJammer jammer) {
        byEntityId.put(jammer.entityId, jammer);
    }

    public ActiveJammer unregister(UUID entityId) {
        return byEntityId.remove(entityId);
    }

    public Collection<ActiveJammer> all() {
        return byEntityId.values();
    }

    public void clear() {
        byEntityId.clear();
    }
}