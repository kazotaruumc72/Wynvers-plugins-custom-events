package com.wynvers.customevents.nexo;
public class WitherProperties {
    private final boolean witherExplosionDamage;
    private final boolean witherDamageThrow;
    public WitherProperties(boolean witherExplosionDamage, boolean witherDamageThrow) {
        this.witherExplosionDamage = witherExplosionDamage;
        this.witherDamageThrow = witherDamageThrow;
    }
    public boolean allowsWitherExplosionDamage() {
        return witherExplosionDamage;
    }
    public boolean allowsWitherDamageThrow() {
        return witherDamageThrow;
    }
}