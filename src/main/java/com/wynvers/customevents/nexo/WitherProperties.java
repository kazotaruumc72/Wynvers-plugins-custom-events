package com.wynvers.customevents.nexo;
public class WitherProperties {
    public static final int CHANCE_UNSET = -1;
    private final boolean witherExplosionDamage;
    private final boolean witherDamageThrow;
    private final int explosionBreakChancePercent;
    private final int skullBreakChancePercent;
    public WitherProperties(boolean witherExplosionDamage, boolean witherDamageThrow,
                            int explosionBreakChancePercent, int skullBreakChancePercent) {
        this.witherExplosionDamage = witherExplosionDamage;
        this.witherDamageThrow = witherDamageThrow;
        this.explosionBreakChancePercent = explosionBreakChancePercent;
        this.skullBreakChancePercent = skullBreakChancePercent;
    }
    public boolean allowsWitherExplosionDamage() {
        return witherExplosionDamage;
    }
    public boolean allowsWitherDamageThrow() {
        return witherDamageThrow;
    }
    public int explosionBreakChancePercent() {
        return explosionBreakChancePercent;
    }
    public int skullBreakChancePercent() {
        return skullBreakChancePercent;
    }
}