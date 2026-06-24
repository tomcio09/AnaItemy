package pl.anaheim.anaitemy.managers;

import org.bukkit.entity.Player;
import pl.anaheim.anaitemy.AnaItemy;

import java.lang.reflect.Method;
import java.util.UUID;

public class CombatIntegrationManager {

    private final AnaItemy plugin;
    private final boolean antylogoutEnabled;

    private Class<?> apiClass;
    private Method wasLogoutDeathMethod;
    private Method wasCombatDeathMethod;
    private Method getKillerOfMethod;
    private Method isPlayerTaggedMethod;
    private Method isRestartingMethod;
    private Method tagPlayerMethod;

    public CombatIntegrationManager(AnaItemy plugin) {
        this.plugin = plugin;

        boolean pluginFound = plugin.getServer().getPluginManager().isPluginEnabled("AnacodeAntylogout");
        boolean configEnabled = plugin.getItemsConfig().isCombatIntegrationEnabled();

        if (pluginFound && configEnabled) {
            this.antylogoutEnabled = initReflection();
        } else {
            this.antylogoutEnabled = false;
        }

        if (antylogoutEnabled) {
            plugin.getLogger().info("[CombatIntegration] AnacodeAntylogout wykryty - integracja WLACZONA!");
        } else if (!pluginFound) {
            plugin.getLogger().info("[CombatIntegration] AnacodeAntylogout nie znaleziony - plugin dziala normalnie.");
        } else {
            plugin.getLogger().info("[CombatIntegration] Integracja wylaczona w configu.");
        }
    }

    private boolean initReflection() {
        String[] possiblePackages = {
            "pl.anacode.antylogout.api.AntylogoutAPI",
            "pl.anacode.antylogout.AntylogoutAPI",
            "pl.anaheim.antylogout.api.AntylogoutAPI",
            "me.anacode.antylogout.api.AntylogoutAPI"
        };

        for (String packageName : possiblePackages) {
            try {
                apiClass = Class.forName(packageName);
                break;
            } catch (ClassNotFoundException ignored) {}
        }

        if (apiClass == null) {
            plugin.getLogger().severe("[CombatIntegration] Nie znaleziono klasy AntylogoutAPI!");
            return false;
        }

        try {
            wasLogoutDeathMethod = apiClass.getMethod("wasLogoutDeath", UUID.class);
            wasCombatDeathMethod = apiClass.getMethod("wasCombatDeath", UUID.class);
            getKillerOfMethod = apiClass.getMethod("getKillerOf", UUID.class);
            isPlayerTaggedMethod = apiClass.getMethod("isPlayerTagged", Player.class);
            isRestartingMethod = apiClass.getMethod("isRestarting");

            try {
                tagPlayerMethod = apiClass.getMethod("tagPlayer", Player.class, Player.class);
            } catch (NoSuchMethodException e1) {
                try {
                    tagPlayerMethod = apiClass.getMethod("startCombat", Player.class, Player.class);
                } catch (NoSuchMethodException e2) {
                    try {
                        tagPlayerMethod = apiClass.getMethod("tagPlayer", Player.class);
                    } catch (NoSuchMethodException e3) {
                        plugin.getLogger().warning("[CombatIntegration] Metoda tagowania nie znaleziona w API.");
                        tagPlayerMethod = null;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[CombatIntegration] Blad inicjalizacji refleksji: " + e.getMessage());
            return false;
        }
    }

    public boolean isInCombat(Player player) {
        if (!antylogoutEnabled || player == null) return false;
        try {
            Object result = isPlayerTaggedMethod.invoke(null, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean wasLogoutDeath(UUID playerUUID) {
        if (!antylogoutEnabled) return false;
        try {
            Object result = wasLogoutDeathMethod.invoke(null, playerUUID);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean wasCombatDeath(UUID playerUUID) {
        if (!antylogoutEnabled) return false;
        try {
            Object result = wasCombatDeathMethod.invoke(null, playerUUID);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    public UUID getKillerOf(UUID victimUUID) {
        if (!antylogoutEnabled) return null;
        try {
            Object result = getKillerOfMethod.invoke(null, victimUUID);
            return result instanceof UUID ? (UUID) result : null;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isRestarting() {
        if (!antylogoutEnabled) return false;
        try {
            Object result = isRestartingMethod.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean tagPlayer(Player victim, Player attacker) {
        if (!antylogoutEnabled || victim == null || tagPlayerMethod == null) return false;
        try {
            int paramCount = tagPlayerMethod.getParameterCount();
            if (paramCount == 2) {
                tagPlayerMethod.invoke(null, victim, attacker);
            } else if (paramCount == 1) {
                tagPlayerMethod.invoke(null, victim);
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad tagowania: " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() { return antylogoutEnabled; }
    public boolean hasTagPlayerMethod() { return tagPlayerMethod != null; }
}
