package com.wynvers.customevents.nexo.teleporter;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Real Nexo {@link Mechanic} implementing the {@code teleporter} block.
 *
 * <p>YAML example (top-level inside {@code Mechanics:}):
 * <pre>
 * teleporter_example:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 476
 *     teleporter:
 *       world: "world_name"
 *       x: "100"
 *       y: "64"
 *       z: "100"
 *       yaw: "0"
 *       pitch: "0"
 *   Pack:
 *     parent_model: block/cube_all
 *     texture: namespace:texture_path
 * </pre>
 */
public class TeleporterMechanic extends Mechanic {

    private final String destinationWorld;
    private final double destinationX;
    private final double destinationY;
    private final double destinationZ;
    private final float yaw;
    private final float pitch;

    public TeleporterMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);

        this.destinationWorld = section.getString("world", "world");
        this.destinationX = safeParseDouble(section.getString("x", "0"));
        this.destinationY = safeParseDouble(section.getString("y", "64"));
        this.destinationZ = safeParseDouble(section.getString("z", "0"));
        this.yaw = (float) safeParseDouble(section.getString("yaw", "0"));
        this.pitch = (float) safeParseDouble(section.getString("pitch", "0"));
    }

    public String destinationWorld() { return destinationWorld; }
    public double destinationX() { return destinationX; }
    public double destinationY() { return destinationY; }
    public double destinationZ() { return destinationZ; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public void teleport(Player player) {
        org.bukkit.World world = Bukkit.getWorld(destinationWorld);
        if (world == null) {
            player.sendMessage("§c[Teleporter] Monde inexistant: " + destinationWorld);
            return;
        }
        player.teleport(new Location(world, destinationX, destinationY, destinationZ, yaw, pitch));
        player.sendMessage("§a[Teleporter] Téléporté!");
    }

    private static double safeParseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

