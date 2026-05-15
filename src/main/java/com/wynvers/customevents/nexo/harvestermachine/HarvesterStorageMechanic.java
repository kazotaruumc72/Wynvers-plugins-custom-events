package com.wynvers.customevents.nexo.harvestermachine;

import com.nexomc.nexo.mechanics.Mechanic;
import com.nexomc.nexo.mechanics.MechanicFactory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Companion mechanic for the Harvester Machine's storage block.
 *
 * <p>Acts as a 54-slot virtual chest. Items deposited by nearby Harvester
 * Machines accumulate here; players can right-click the block to open the
 * GUI; a hopper placed directly below pulls items at the usual hopper rate.
 *
 * <p>YAML example:
 * <pre>
 * wynvers_harvester_storage:
 *   Mechanics:
 *     custom_block:
 *       type: NOTEBLOCK
 *       custom_variation: 351
 *     harvester_storage:
 *       gui_title: "&8Coffre du Récolteur"
 *       hopper_pull_period_ticks: 8   # how often a hopper below pulls one item
 * </pre>
 */
public class HarvesterStorageMechanic extends Mechanic {

    private final String guiTitle;
    private final int hopperPullPeriodTicks;
    private final double pickupRadius;
    private final Set<String> whitelistNexoIds;
    private final Set<Material> whitelistMaterials;
    private final boolean whitelistEmpty;

    public HarvesterStorageMechanic(MechanicFactory factory, ConfigurationSection section) {
        super(factory, section);
        this.guiTitle = section.getString("gui_title", "&8Coffre du Récolteur");
        this.hopperPullPeriodTicks = Math.max(1, section.getInt("hopper_pull_period_ticks", 8));
        this.pickupRadius = Math.max(0.5, Math.min(10.0, section.getDouble("pickup_radius", 5.0)));

        Set<String> nexo = new LinkedHashSet<>();
        Set<Material> mats = new LinkedHashSet<>();
        List<String> raw = section.getStringList("pickup_whitelist");
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) continue;
            String e = entry.trim();
            if (e.toLowerCase(Locale.ROOT).startsWith("nexo:")) {
                nexo.add(e.substring(5).trim().toLowerCase(Locale.ROOT));
            } else {
                try { mats.add(Material.valueOf(e.toUpperCase(Locale.ROOT))); }
                catch (IllegalArgumentException ignored) {}
            }
        }
        this.whitelistNexoIds   = Collections.unmodifiableSet(nexo);
        this.whitelistMaterials = Collections.unmodifiableSet(mats);
        this.whitelistEmpty     = nexo.isEmpty() && mats.isEmpty();
    }

    public String guiTitle()              { return guiTitle; }
    public int hopperPullPeriodTicks()    { return hopperPullPeriodTicks; }
    /** All-directions radius (in blocks) within which dropped Item entities are absorbed. */
    public double pickupRadius()          { return pickupRadius; }

    /**
     * Decides whether an ItemStack may be absorbed.
     * If no whitelist is configured, everything is allowed (back-compat).
     */
    public boolean isAllowed(org.bukkit.inventory.ItemStack stack) {
        if (whitelistEmpty) return true;
        if (stack == null || stack.getType().isAir()) return false;
        try {
            String nexoId = com.nexomc.nexo.api.NexoItems.idFromItem(stack);
            if (nexoId != null && whitelistNexoIds.contains(nexoId.toLowerCase(Locale.ROOT))) return true;
        } catch (Throwable ignored) {
            // Nexo absent
        }
        return whitelistMaterials.contains(stack.getType());
    }
}