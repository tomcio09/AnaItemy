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

        // ✅ POPRAWIONO: AnacodeAntylogout zamiast Antylogout
        boolean pluginFound = plugin.getServer().getPluginManager().isPluginEnabled("AnacodeAntylogout");
        boolean configEnabled = plugin.getItemsConfig().isCombatIntegrationEnabled();

        plugin.getLogger().info("[CombatIntegration] Szukam pluginu: AnacodeAntylogout...");
        plugin.getLogger().info("[CombatIntegration] Plugin znaleziony: " + pluginFound);
        plugin.getLogger().info("[CombatIntegration] Config enabled: " + configEnabled);

        if (pluginFound && configEnabled) {
            this.antylogoutEnabled = initReflection();
        } else {
            this.antylogoutEnabled = false;
        }

        if (antylogoutEnabled) {
            plugin.getLogger().info("[CombatIntegration] ✅ AnacodeAntylogout wykryty - integracja WLACZONA!");
        } else if (!pluginFound) {
            plugin.getLogger().warning("[CombatIntegration] ❌ AnacodeAntylogout nie znaleziony!");
        } else if (!configEnabled) {
            plugin.getLogger().info("[CombatIntegration] Integracja wylaczona w configu.");
        }
    }

    private boolean initReflection() {
        plugin.getLogger().info("[CombatIntegration] Inicjalizacja refleksji...");
        
        // ✅ Próbuj różne package'y
        String[] possiblePackages = {
            "pl.anacode.antylogout.api.AntylogoutAPI",
            "pl.anacode.antylogout.AntylogoutAPI",
            "pl.anaheim.antylogout.api.AntylogoutAPI",
            "me.anacode.antylogout.api.AntylogoutAPI"
        };

        for (String packageName : possiblePackages) {
            try {
                plugin.getLogger().info("[CombatIntegration] Probuje package: " + packageName);
                apiClass = Class.forName(packageName);
                plugin.getLogger().info("[CombatIntegration] ✅ Znaleziono klase API: " + packageName);
                break;
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("[CombatIntegration] Nie znaleziono: " + packageName);
            }
        }

        if (apiClass == null) {
            plugin.getLogger().severe("[CombatIntegration] ❌ Nie znaleziono klasy AntylogoutAPI w żadnym package!");
            plugin.getLogger().severe("[CombatIntegration] Sprawdz kod AnacodeAntylogout i podaj mi dokladny package!");
            return false;
        }

        try {
            wasLogoutDeathMethod = apiClass.getMethod("wasLogoutDeath", UUID.class);
            plugin.getLogger().info("[CombatIntegration] ✅ wasLogoutDeath() znaleziona!");

            wasCombatDeathMethod = apiClass.getMethod("wasCombatDeath", UUID.class);
            plugin.getLogger().info("[CombatIntegration] ✅ wasCombatDeath() znaleziona!");

            getKillerOfMethod = apiClass.getMethod("getKillerOf", UUID.class);
            plugin.getLogger().info("[CombatIntegration] ✅ getKillerOf() znaleziona!");

            isPlayerTaggedMethod = apiClass.getMethod("isPlayerTagged", Player.class);
            plugin.getLogger().info("[CombatIntegration] ✅ isPlayerTagged() znaleziona!");

            isRestartingMethod = apiClass.getMethod("isRestarting");
            plugin.getLogger().info("[CombatIntegration] ✅ isRestarting() znaleziona!");

            // Próbuj różne warianty tagowania
            try {
                tagPlayerMethod = apiClass.getMethod("tagPlayer", Player.class, Player.class);
                plugin.getLogger().info("[CombatIntegration] ✅ tagPlayer(Player, Player) znaleziona!");
            } catch (NoSuchMethodException e1) {
                try {
                    tagPlayerMethod = apiClass.getMethod("startCombat", Player.class, Player.class);
                    plugin.getLogger().info("[CombatIntegration] ✅ startCombat(Player, Player) znaleziona!");
                } catch (NoSuchMethodException e2) {
                    try {
                        tagPlayerMethod = apiClass.getMethod("tagPlayer", Player.class);
                        plugin.getLogger().info("[CombatIntegration] ✅ tagPlayer(Player) znaleziona!");
                    } catch (NoSuchMethodException e3) {
                        plugin.getLogger().warning("[CombatIntegration] ⚠ Metoda tagowania NIE znaleziona!");
                        tagPlayerMethod = null;
                    }
                }
            }

            plugin.getLogger().info("[CombatIntegration] ✅ Refleksja zainicjalizowana pomyslnie!");
            return true;

        } catch (NoSuchMethodException e) {
            plugin.getLogger().severe("[CombatIntegration] ❌ Brak wymaganych metod w API!");
            plugin.getLogger().severe("[CombatIntegration] Missing method: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean isInCombat(Player player) {
        if (!antylogoutEnabled || player == null) {
            return false;
        }

        try {
            Object result = isPlayerTaggedMethod.invoke(null, player);
            boolean inCombat = result instanceof Boolean && (Boolean) result;

            // ✅ LOGUJ TYLKO gdy TRUE (żeby nie spamować)
            if (inCombat) {
                plugin.getLogger().info("[CombatIntegration] ✅ " + player.getName() + " JEST W WALCE!");
            }

            return inCombat;
        } catch (Exception e) {
            plugin.getLogger().severe("[CombatIntegration] BLAD isInCombat: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean wasLogoutDeath(UUID playerUUID) {
        if (!antylogoutEnabled) return false;

        try {
            Object result = wasLogoutDeathMethod.invoke(null, playerUUID);
            boolean wasLogout = result instanceof Boolean && (Boolean) result;

            if (wasLogout) {
                plugin.getLogger().info("[CombatIntegration] ✅ Gracz UUID:" + playerUUID + " zginął przez WYLOGOWANIE!");
            }

            return wasLogout;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad wasLogoutDeath: " + e.getMessage());
            return false;
        }
    }

    public boolean wasCombatDeath(UUID playerUUID) {
        if (!antylogoutEnabled) return false;

        try {
            Object result = wasCombatDeathMethod.invoke(null, playerUUID);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad wasCombatDeath: " + e.getMessage());
            return false;
        }
    }

    public UUID getKillerOf(UUID victimUUID) {
        if (!antylogoutEnabled) return null;

        try {
            Object result = getKillerOfMethod.invoke(null, victimUUID);
            return result instanceof UUID ? (UUID) result : null;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad getKillerOf: " + e.getMessage());
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
        if (!antylogoutEnabled || victim == null) {
            return false;
        }

        if (tagPlayerMethod == null) {
            plugin.getLogger().warning("[CombatIntegration] tagPlayer() -> metoda NIE istnieje w API");
            return false;
        }

        try {
            int paramCount = tagPlayerMethod.getParameterCount();

            if (paramCount == 2) {
                tagPlayerMethod.invoke(null, victim, attacker);
                plugin.getLogger().info("[CombatIntegration] ✅ Ztagowano " + victim.getName() +
                        (attacker != null ? " przez " + attacker.getName() : " (Hydroklatka)"));
            } else if (paramCount == 1) {
                tagPlayerMethod.invoke(null, victim);
                plugin.getLogger().info("[CombatIntegration] ✅ Ztagowano " + victim.getName());
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[CombatIntegration] BLAD tagowania " + victim.getName());
            e.printStackTrace();
            return false;
        }
    }

    public boolean isEnabled() {
        return antylogoutEnabled;
    }

    public boolean hasTagPlayerMethod() {
        return tagPlayerMethod != null;
    }
}
