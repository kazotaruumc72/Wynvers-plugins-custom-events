package com.wynvers.customevents.nexo.runeshop;

import fr.maxlego08.shop.api.event.ShopAction;
import fr.maxlego08.shop.api.event.events.ZShopBuyEvent;
import fr.maxlego08.shop.api.event.events.ZShopSellAllEvent;
import fr.maxlego08.shop.api.event.events.ZShopSellEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Adjusts zShop transaction prices based on the player's active shop runes.
 *
 * <p>Let {@code k = sum/100} be the total active rune percent on the wearer.
 * Sell price is multiplied by {@code (1 + k)} (more money on sell). Buy price
 * is multiplied by {@code max(0.5, 1 / (1 + k))} — a multiplicative discount
 * <strong>capped at 50% off</strong>, so the rune can never reduce the buy
 * price below half. Examples: 50% rune → 33% off, 100% rune → 50% off (cap),
 * 300% rune → still 50% off.
 *
 * <p>This class is only registered when the zShop plugin is present, so that
 * the rest of the rune mechanic loads cleanly without zShop on the classpath.
 */
public class ShopRuneZShopListener implements Listener {

    private final ShopRuneMechanicFactory factory;

    public ShopRuneZShopListener(ShopRuneMechanicFactory factory) {
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBuy(ZShopBuyEvent event) {
        Player player = event.getPlayer();
        int sum = factory.sumActivePercent(player);
        if (sum <= 0) return;
        double mult = Math.max(0.5, 1.0 / (1.0 + sum / 100.0));
        event.setPrice(event.getPrice() * mult);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSell(ZShopSellEvent event) {
        Player player = event.getPlayer();
        int sum = factory.sumActivePercent(player);
        if (sum <= 0) return;
        event.setPrice(event.getPrice() * (1.0 + sum / 100.0));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSellAll(ZShopSellAllEvent event) {
        Player player = event.getPlayer();
        int sum = factory.sumActivePercent(player);
        if (sum <= 0) return;
        double mult = 1.0 + sum / 100.0;
        for (ShopAction a : event.getShopActions()) {
            a.setPrice(a.getPrice() * mult);
        }
    }
}