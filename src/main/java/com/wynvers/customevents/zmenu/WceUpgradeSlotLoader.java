package com.wynvers.customevents.zmenu;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Loader for the {@code wce_upgrade_slot} zMenu button.
 *
 * <p>Registers as the button type {@code WCE_UPGRADE_SLOT} (case-insensitive
 * — zMenu lower-cases names internally). The button is a pure layout marker:
 * the WynversCustomEvents BlockBreaker reads the slot positions from the
 * loaded zMenu {@code blockbreaker.yml} and renders a real interactive slot
 * at each of those positions in a separate Bukkit inventory.
 */
public class WceUpgradeSlotLoader extends ButtonLoader {

    public WceUpgradeSlotLoader(@NotNull Plugin plugin) {
        super(plugin, BlockBreakerZMenuBridge.BUTTON_UPGRADE_SLOT);
    }

    @Override
    public Button load(@NotNull YamlConfiguration configuration,
                       @NotNull String path,
                       @NotNull DefaultButtonValue defaultButtonValue) {
        return new WceUpgradeSlotButton();
    }
}