package com.wynvers.customevents.axpickaxes;

import com.wynvers.customevents.WynversCustomEvents;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Hooks AxPickaxes' integration manager so its ores.yml can reference Nexo
 * custom blocks via {@code NEXO:<block_id>}.
 *
 * AxPickaxes embeds AxIntegrations under
 * {@code com.artillexstudios.axpickaxes.libs.axintegrations.*}, and the
 * built-in {@code NexoCustomBlock} integration ships with it. It only becomes
 * usable once provided via {@code AxIntegrationsAPI.registerIntegration(...)}
 * during the one-shot {@code AxIntegrationsLoadEvent} — registrations after
 * the event throw {@code IntegrationsLockedException}. {@code loadbefore:
 * AxPickaxes} in plugin.yml guarantees we install our listener before
 * AxPickaxes fires it.
 *
 * Everything goes through reflection so this plugin still builds when
 * AxPickaxes is absent.
 */
public final class AxPickaxesNexoIntegrator {

    private static final String BASE =
            "com.artillexstudios.axpickaxes.libs.axintegrations";

    private AxPickaxesNexoIntegrator() {}

    public static void register(WynversCustomEvents plugin) {
        if (Bukkit.getPluginManager().getPlugin("AxPickaxes") == null) return;
        if (Bukkit.getPluginManager().getPlugin("Nexo") == null) {
            plugin.getLogger().info("AxPickaxes detected but Nexo absent — "
                    + "skipping NEXO custom-block source registration.");
            return;
        }

        Class<? extends Event> loadEventClass;
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> klass = (Class<? extends Event>)
                    Class.forName(BASE + ".api.events.AxIntegrationsLoadEvent");
            loadEventClass = klass;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("AxPickaxes integration: "
                    + BASE + ".api.events.AxIntegrationsLoadEvent not found. "
                    + "AxPickaxes version may use a different relocation — "
                    + "NEXO:<block_id> in ores.yml will not be wired up.");
            return;
        }

        Listener marker = new Listener() {};
        EventExecutor executor = (listener, event) -> provideNexo(plugin);

        Bukkit.getPluginManager().registerEvent(
                loadEventClass, marker, EventPriority.NORMAL, executor, plugin);

        plugin.getLogger().info("Waiting on AxIntegrationsLoadEvent to "
                + "register NEXO custom-block source with AxPickaxes.");
    }

    private static void provideNexo(WynversCustomEvents plugin) {
        try {
            Class<?> apiClass = Class.forName(BASE + ".api.AxIntegrationsAPI");
            Class<?> integrationBase = Class.forName(BASE + ".Integration");
            Class<?> nexoClass = Class.forName(BASE + ".integrations.NexoCustomBlock");

            Constructor<?> ctor = nexoClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();

            Method register = apiClass.getMethod("registerIntegration", integrationBase);
            register.invoke(null, instance);

            plugin.getLogger().info("Registered NEXO custom-block source with "
                    + "AxPickaxes — NEXO:<block_id> is now usable in ores.yml.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("AxPickaxes does not bundle the "
                    + "NexoCustomBlock integration class (" + e.getMessage()
                    + "). NEXO source not registered.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to register NEXO source with "
                    + "AxPickaxes: " + t);
        }
    }
}