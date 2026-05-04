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
 * <p>Sell price is multiplied by {@code (1 + sum/100)} (more money on sell)
 * and buy price by {@code (1 - sum/100)} (cheaper on buy), where {@code sum}
 * is the total of all active rune percentages on the player's equipped armor.
 * Buy price is clamped to {@code >= 0} so a 100%+ total simply makes the item
 * free, never negative.
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
        double newPrice = event.getPrice() * (1.0 - sum / 100.0);
        if (newPrice < 0) newPrice = 0;
        event.setPrice(newPrice);
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