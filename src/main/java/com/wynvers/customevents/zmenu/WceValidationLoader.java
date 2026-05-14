package com.wynvers.customevents.zmenu;

import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.DefaultButtonValue;
import fr.maxlego08.menu.api.loader.ButtonLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Loader for the {@code wce_validation} zMenu button.
 *
 * <p>Registers as the button type {@code WCE_VALIDATION}. The actual click
 * handling lives in {@link com.wynvers.customevents.nexo.blockbreaker.BlockBreakerUpgradeMenuListener}
 * because we open a vanilla Bukkit inventory rather than the zMenu rendered
 * one; zMenu only defines the layout.
 */
public class WceValidationLoader extends ButtonLoader {

    public WceValidationLoader(@NotNull Plugin plugin) {
        super(plugin, BlockBreakerZMenuBridge.BUTTON_VALIDATION);
    }

    @Override
    public Button load(@NotNull YamlConfiguration configuration,
                       @NotNull String path,
                       @NotNull DefaultButtonValue defaultButtonValue) {
        return new WceValidationButton();
    }
}