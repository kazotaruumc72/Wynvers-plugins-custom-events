package com.wynvers.customevents.listener;
import com.wynvers.customevents.WynversCustomEvents;
import com.wynvers.customevents.nexo.NexoWitherPropertiesLoader;
import com.wynvers.customevents.nexo.WitherProperties;
import com.wynvers.customevents.nexo.WitherPropertiesMechanic;
import com.wynvers.customevents.nexo.WitherPropertiesMechanicFactory;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import java.util.Iterator;
/**
 * Removes Nexo custom blocks from Wither / Wither-skull explosions when their
 * {@code wither_properties} mechanic forbids that damage source.
 *
 * <p>The mechanic is looked up in two places, in order:
 * <ol>
 *   <li>Nexo's own {@link WitherPropertiesMechanicFactory} registry (the
 *       canonical source once Nexo has parsed every item).</li>
 *   <li>{@link NexoWitherPropertiesLoader} – a fallback that reads the YAML
 *       files directly, used when Nexo hasn't parsed our mechanic yet
 *       (e.g. before {@code /nexo reload}).</li>
 * </ol>
 */
public class WitherEventListener implements Listener {
    private final WynversCustomEvents plugin;
    private final NexoWitherPropertiesLoader fallbackLoader;
    public WitherEventListener(WynversCustomEvents plugin, NexoWitherPropertiesLoader fallbackLoader) {
        this.plugin = plugin;
        this.fallbackLoader = fallbackLoader;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        EntityType type = event.getEntity().getType();
        boolean isWitherBody  = (type == EntityType.WITHER);
        boolean isWitherSkull = (type == EntityType.WITHER_SKULL);
        if (!isWitherBody && !isWitherSkull) return;
        Iterator<Block> it = event.blockList().iterator();
        while (it.hasNext()) {
            Block block = it.next();
            Resolution res = resolve(block);
            if (res == null) continue;
            if (isWitherBody  && !res.allowsExplosion()) it.remove();
            else if (isWitherSkull && !res.allowsSkull())   it.remove();
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWitherChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.WITHER) return;
        Resolution res = resolve(event.getBlock());
        if (res == null) return;
        if (!res.allowsExplosion()) {
            event.setCancelled(true);
        }
    }
    // -------------------------------------------------------------------------
    // Resolution: prefers the Nexo Mechanic, falls back to the YAML loader
    // -------------------------------------------------------------------------
    private Resolution resolve(Block block) {
        try {
            if (!com.nexomc.nexo.api.NexoBlocks.isCustomBlock(block)) return null;
            var customMech = com.nexomc.nexo.api.NexoBlocks.customBlockMechanic(block);
            if (customMech == null) return null;
            String itemId = customMech.getItemID();
            // 1. Official Nexo mechanic (set after Nexo parsed our factory).
            WitherPropertiesMechanicFactory factory = WitherPropertiesMechanicFactory.instance();
            if (factory != null) {
                WitherPropertiesMechanic m = factory.getMechanic(itemId);
                if (m != null) {
                    return new Resolution(m.allowsWitherExplosionDamage(), m.allowsWitherDamageThrow());
                }
            }
            // 2. YAML fallback.
            if (fallbackLoader != null) {
                WitherProperties props = fallbackLoader.getProperties(itemId);
                if (props != null) {
                    return new Resolution(props.allowsWitherExplosionDamage(), props.allowsWitherDamageThrow());
                }
            }
            return null;
        } catch (Exception e) {
            plugin.getLogger().fine(
                    "[WitherProperties] Error checking block at " + block.getLocation() + ": " + e.getMessage());
            return null;
        }
    }
    private record Resolution(boolean allowsExplosion, boolean allowsSkull) {}
}