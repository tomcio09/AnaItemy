package pl.anaheim.anaitemy.managers;

import org.bukkit.entity.Player;
import pl.anaheim.anaitemy.AnaItemy;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * ✅ Manager integracji z pluginem walki (Antylogout).
 * Używa REFLEKSJI - nie wymaga JARa w compile time.
 * Jeśli Antylogout nie jest na serwerze - wszystkie metody zwracają false/null.
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

            wasLogoutDeathMethod = apiClass.getMethod("wasLogoutDeath", UUID.class);
            wasCombatDeathMethod = apiClass.getMethod("wasCombatDeath", UUID.class);
            getKillerOfMethod = apiClass.getMethod("getKillerOf", UUID.class);
            isPlayerTaggedMethod = apiClass.getMethod("isPlayerTagged", Player.class);
            isRestartingMethod = apiClass.getMethod("isRestarting");

            plugin.getLogger().info("[CombatIntegration] Refleksja zainicjalizowana pomyslnie!");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[CombatIntegration] Klasa AntylogoutAPI nie znaleziona: " + e.getMessage());
            return false;
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("[CombatIntegration] Metoda API nie znaleziona: " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad inicjalizacji refleksji: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sprawdza czy gracz jest aktualnie w walce.
     */
    public boolean isInCombat(Player player) {
        if (!antylogoutEnabled) return false;

        try {
            Object result = isPlayerTaggedMethod.invoke(null, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad sprawdzania combat: " + e.getMessage());
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
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad sprawdzania logout death: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sprawdza czy śmierć gracza była w walce (dowolna przyczyna).
     */
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

    /**
     * Pobiera UUID zabójcy gracza (z combat pluginu).
     */
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

    /**
     * Sprawdza czy trwa safe restart.
     */
    public boolean isRestarting() {
        if (!antylogoutEnabled) return false;

        try {
            Object result = isRestartingMethod.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return antylogoutEnabled;
    }
}
