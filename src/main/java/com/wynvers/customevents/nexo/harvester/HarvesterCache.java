package com.wynvers.customevents.nexo.harvester;

import java.util.HashSet;
import java.util.Set;

/**
 * Cache for storing harvester-enabled items that are on the final stage.
 * This allows quick lookups without repeated checks of the farmer mechanic.
 */
public class HarvesterCache {

    private static final Set<String> FINAL_STAGE_HARVESTERS = new HashSet<>();

    /**
     * Registers an item ID as a final-stage harvester item.
     * @param itemId the Nexo item ID
     */
    public static void registerFinalStageItem(String itemId) {
        if (itemId != null && !itemId.isBlank()) {
            FINAL_STAGE_HARVESTERS.add(itemId);
        }
    }

    /**
     * Checks if an item is a final-stage harvester item.
     * @param itemId the Nexo item ID
     * @return true if the item is registered as a final-stage harvester
     */
    public static boolean isFinalStageHarvester(String itemId) {
        return itemId != null && FINAL_STAGE_HARVESTERS.contains(itemId);
    }

    /**
     * Clears the cache (useful for reloads).
     */
    public static void clear() {
        FINAL_STAGE_HARVESTERS.clear();
    }

    /**
     * Gets the number of cached final-stage harvesters.
     */
    public static int size() {
        return FINAL_STAGE_HARVESTERS.size();
    }
}

