package pl.anaheim.anaitemy.managers;

import org.bukkit.entity.Player;
import pl.anaheim.anaitemy.AnaItemy;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * ✅ Manager integracji z pluginem walki (Antylogout).
 * Używa REFLEKSJI - nie wymaga JARa w compile time.
 */
public class CombatIntegrationManager {

    private final AnaItemy plugin;
    private final boolean antylogoutEnabled;

    // ✅ Refleksja - cache metod API
    private Class<?> apiClass;
    private Method wasLogoutDeathMethod;
    private Method wasCombatDeathMethod;
    private Method getKillerOfMethod;
    private Method isPlayerTaggedMethod;
    private Method isRestartingMethod;
    private Method tagPlayerMethod;

    public CombatIntegrationManager(AnaItemy plugin) {
        this.plugin = plugin;

        boolean pluginFound = plugin.getServer().getPluginManager().isPluginEnabled("Antylogout");
        boolean configEnabled = plugin.getItemsConfig().isCombatIntegrationEnabled();

        if (pluginFound && configEnabled) {
            this.antylogoutEnabled = initReflection();
        } else {
            this.antylogoutEnabled = false;
        }

        if (antylogoutEnabled) {
            plugin.getLogger().info("[CombatIntegration] Antylogout wykryty - integracja wlaczona!");
        } else if (!pluginFound) {
            plugin.getLogger().info("[CombatIntegration] Antylogout nie znaleziony - plugin dziala normalnie bez integracji.");
        } else if (!configEnabled) {
            plugin.getLogger().info("[CombatIntegration] Integracja wylaczona w configu.");
        }
    }

    /**
     * ✅ Inicjalizuj refleksję - znajdź klasy i metody API.
     */
    private boolean initReflection() {
        try {
            apiClass = Class.forName("pl.anacode.antylogout.api.AntylogoutAPI");
            plugin.getLogger().info("[CombatIntegration] Klasa AntylogoutAPI znaleziona!");

            wasLogoutDeathMethod = apiClass.getMethod("wasLogoutDeath", UUID.class);
            plugin.getLogger().info("[CombatIntegration] Metoda wasLogoutDeath() znaleziona!");

            wasCombatDeathMethod = apiClass.getMethod("wasCombatDeath", UUID.class);
            plugin.getLogger().info("[CombatIntegration] Metoda wasCombatDeath() znaleziona!");

            getKillerOfMethod = apiClass.getMethod("getKillerOf", UUID.class);
            plugin.getLogger().info("[CombatIntegration] Metoda getKillerOf() znaleziona!");

            isPlayerTaggedMethod = apiClass.getMethod("isPlayerTagged", Player.class);
            plugin.getLogger().info("[CombatIntegration] Metoda isPlayerTagged() znaleziona!");

            isRestartingMethod = apiClass.getMethod("isRestarting");
            plugin.getLogger().info("[CombatIntegration] Metoda isRestarting() znaleziona!");

            // ✅ Nowa metoda do tagowania graczy - próbuj różne warianty
            try {
                tagPlayerMethod = apiClass.getMethod("tagPlayer", Player.class, Player.class);
                plugin.getLogger().info("[CombatIntegration] Metoda tagPlayer(Player, Player) znaleziona!");
            } catch (NoSuchMethodException e1) {
                try {
                    tagPlayerMethod = apiClass.getMethod("startCombat", Player.class, Player.class);
                    plugin.getLogger().info("[CombatIntegration] Metoda startCombat(Player, Player) znaleziona!");
                } catch (NoSuchMethodException e2) {
                    try {
                        tagPlayerMethod = apiClass.getMethod("tagPlayer", Player.class);
                        plugin.getLogger().info("[CombatIntegration] Metoda tagPlayer(Player) znaleziona!");
                    } catch (NoSuchMethodException e3) {
                        plugin.getLogger().warning("[CombatIntegration] Metoda do tagowania NIE znaleziona - sprawdz API Antylogout!");
                        plugin.getLogger().warning("[CombatIntegration] Probowane: tagPlayer(Player, Player), startCombat(Player, Player), tagPlayer(Player)");
                        tagPlayerMethod = null;
                    }
                }
            }

            plugin.getLogger().info("[CombatIntegration] Refleksja zainicjalizowana pomyslnie!");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("[CombatIntegration] Klasa AntylogoutAPI nie znaleziona!");
            plugin.getLogger().severe("[CombatIntegration] Sprawdz czy package to: pl.anacode.antylogout.api.AntylogoutAPI");
            return false;
        } catch (NoSuchMethodException e) {
            plugin.getLogger().severe("[CombatIntegration] Metoda API nie znaleziona: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("[CombatIntegration] Blad inicjalizacji refleksji: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sprawdza czy gracz jest aktualnie w walce.
     */
    public boolean isInCombat(Player player) {
        if (!antylogoutEnabled || player == null) {
            plugin.getLogger().info("[CombatIntegration] isInCombat() -> false (plugin wylaczony lub player null)");
            return false;
        }

        try {
            Object result = isPlayerTaggedMethod.invoke(null, player);
            boolean inCombat = result instanceof Boolean && (Boolean) result;

            // ✅ ZAWSZE LOGUJ
            plugin.getLogger().info("[CombatIntegration] isInCombat(" + player.getName() + ") -> " + inCombat);

            return inCombat;
        } catch (Exception e) {
            plugin.getLogger().severe("[CombatIntegration] BLAD sprawdzania combat dla " + player.getName());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sprawdza czy śmierć gracza była spowodowana wylogowaniem podczas walki.
     */
    public boolean wasLogoutDeath(UUID playerUUID) {
        if (!antylogoutEnabled) return false;

        try {
            Object result = wasLogoutDeathMethod.invoke(null, playerUUID);
            boolean wasLogout = result instanceof Boolean && (Boolean) result;
            
            if (wasLogout) {
                plugin.getLogger().info("[CombatIntegration] wasLogoutDeath(" + playerUUID + ") -> true");
            }
            
            return wasLogout;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad sprawdzania logout death: " + e.getMessage());
            return false;
        }
    }

    public boolean wasCombatDeath(UUID playerUUID) {
        if (!antylogoutEnabled) return false;

        try {
            Object result = wasCombatDeathMethod.invoke(null, playerUUID);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad sprawdzania combat death: " + e.getMessage());
            return false;
        }
    }

    public UUID getKillerOf(UUID victimUUID) {
        if (!antylogoutEnabled) return null;

        try {
            Object result = getKillerOfMethod.invoke(null, victimUUID);
            return result instanceof UUID ? (UUID) result : null;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad pobierania killera: " + e.getMessage());
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

    /**
     * ✅ NOWA METODA: Taguje gracza (rozpoczyna combat tag).
     */
    public boolean tagPlayer(Player victim, Player attacker) {
        if (!antylogoutEnabled || victim == null) {
            plugin.getLogger().warning("[CombatIntegration] tagPlayer() -> false (plugin wylaczony lub victim null)");
            return false;
        }

        if (tagPlayerMethod == null) {
            plugin.getLogger().warning("[CombatIntegration] tagPlayer() -> false (metoda nie znaleziona w API)");
            return false;
        }

        try {
            // ✅ Sprawdź ile parametrów metoda przyjmuje
            int paramCount = tagPlayerMethod.getParameterCount();
            
            if (paramCount == 2) {
                // tagPlayer(Player, Player) lub startCombat(Player, Player)
                tagPlayerMethod.invoke(null, victim, attacker);
                plugin.getLogger().info("[CombatIntegration] ✅ Ztagowano gracza " + victim.getName() +
                        (attacker != null ? " przez " + attacker.getName() : " przez Hydroklatke"));
            } else if (paramCount == 1) {
                // tagPlayer(Player)
                tagPlayerMethod.invoke(null, victim);
                plugin.getLogger().info("[CombatIntegration] ✅ Ztagowano gracza " + victim.getName() + " (bez attackera)");
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("[CombatIntegration] BLAD tagowania gracza " + victim.getName());
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
