package com.wynvers.customevents.nexo.blockbreaker;

import com.nexomc.nexo.api.NexoItems;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * YAML-backed persistence for BlockBreaker states.
 *
 * <p>Each placed breaker is stored under {@code breakers.<sanitised-key>} with:
 * <ul>
 *   <li>{@code loc}            — the original {@code worldUid:x:y:z} key</li>
 *   <li>{@code nexoId}         — the Nexo item id (e.g. {@code wynvers_block_breaker})</li>
 *   <li>{@code ownerId}        — the player who activated the breaker</li>
 *   <li>{@code activeFaces}    — list of {@link BlockFace} names</li>
 *   <li>{@code upgrades}       — map of {@link BlockBreakerUpgrade.Type} → int level</li>
 *   <li>{@code upgradeItems}   — map of slot-index → Nexo item id, so the GUI
 *       can re-render exactly what the player committed</li>
 * </ul>
 *
 * <p>Save is called on every state-changing interaction (face toggle, GUI
 * validation, breaker break). Load runs once after Nexo is ready.
 */
public class BlockBreakerStore {

    private final JavaPlugin plugin;
    private final File file;

    public BlockBreakerStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "breakers.yml");
    }

    public synchronized void saveAll(Map<String, BlockBreakerManager.State> states) {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, BlockBreakerManager.State> e : states.entrySet()) {
            ConfigurationSection sec = cfg.createSection("breakers." + sanitise(e.getKey()));
            BlockBreakerManager.State s = e.getValue();
            sec.set("loc", e.getKey());
            sec.set("nexoId", s.nexoId);
            sec.set("ownerId", s.ownerId != null ? s.ownerId.toString() : null);

            List<String> faces = new ArrayList<>();
            for (BlockFace f : s.activeFaces) faces.add(f.name());
            sec.set("activeFaces", faces);

            Map<String, Integer> upgrades = new LinkedHashMap<>();
            for (Map.Entry<BlockBreakerUpgrade.Type, Integer> u : s.upgrades.entrySet()) {
                upgrades.put(u.getKey().name(), u.getValue());
            }
            sec.set("upgrades", upgrades);

            Map<String, String> items = new LinkedHashMap<>();
            for (int i = 0; i < s.upgradeItems.length; i++) {
                ItemStack it = s.upgradeItems[i];
                if (it == null) continue;
                String id = NexoItems.idFromItem(it);
                if (id != null) items.put(Integer.toString(i), id);
            }
            sec.set("upgradeItems", items);
        }
        try { cfg.save(file); }
        catch (IOException io) {
            plugin.getLogger().warning("Failed to save breakers.yml: " + io.getMessage());
        }
    }

    public synchronized Map<String, BlockBreakerManager.State> loadAll() {
        Map<String, BlockBreakerManager.State> out = new HashMap<>();
        if (!file.exists()) return out;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection breakers = cfg.getConfigurationSection("breakers");
        if (breakers == null) return out;

        for (String key : breakers.getKeys(false)) {
            ConfigurationSection sec = breakers.getConfigurationSection(key);
            if (sec == null) continue;

            String loc = sec.getString("loc");
            String nexoId = sec.getString("nexoId");
            if (loc == null || nexoId == null) continue;

            UUID ownerId = parseUuid(sec.getString("ownerId"));
            BlockBreakerManager.State s = new BlockBreakerManager.State(nexoId, ownerId);

            for (String f : sec.getStringList("activeFaces")) {
                try { s.activeFaces.add(BlockFace.valueOf(f)); }
                catch (IllegalArgumentException ignored) {}
            }

            ConfigurationSection ups = sec.getConfigurationSection("upgrades");
            if (ups != null) {
                for (String t : ups.getKeys(false)) {
                    try { s.upgrades.put(BlockBreakerUpgrade.Type.valueOf(t), ups.getInt(t)); }
                    catch (IllegalArgumentException ignored) {}
                }
            }

            ConfigurationSection items = sec.getConfigurationSection("upgradeItems");
            if (items != null) {
                for (String k : items.getKeys(false)) {
                    int idx;
                    try { idx = Integer.parseInt(k); }
                    catch (NumberFormatException e) { continue; }
                    if (idx < 0 || idx >= s.upgradeItems.length) continue;
                    String id = items.getString(k);
                    if (id == null || id.isEmpty()) continue;
                    try {
                        var builder = NexoItems.itemFromId(id);
                        if (builder != null) s.upgradeItems[idx] = builder.build();
                    } catch (Throwable ignored) {}
                }
            }

            out.put(loc, s);
        }
        return out;
    }

    private static String sanitise(String key) {
        return key.replace(':', '_').replace('.', '_').replace('-', '_');
    }

    private static UUID parseUuid(String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); }
        catch (IllegalArgumentException e) { return null; }
    }
}