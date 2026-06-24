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
    private Method tagPlayerMethod; // ✅ NOWA METODA

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

            // ✅ Nowa metoda do tagowania graczy
            try {
                tagPlayerMethod = apiClass.getMethod("tagPlayer", Player.class, Player.class);
                plugin.getLogger().info("[CombatIntegration] Metoda tagPlayer() znaleziona!");
            } catch (NoSuchMethodException e) {
                plugin.getLogger().warning("[CombatIntegration] Metoda tagPlayer(Player, Player) nie znaleziona - tagowanie w Hydroklatce wylaczone!");
                tagPlayerMethod = null;
            }

            plugin.getLogger().info("[CombatIntegration] Refleksja zainicjalizowana pomyslnie!");
            return true;

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[CombatIntegration] Klasa AntylogoutAPI nie znaleziona!");
            return false;
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("[CombatIntegration] Metoda API nie znaleziona: " + e.getMessage());
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad inicjalizacji refleksji: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sprawdza czy gracz jest aktualnie w walce.
     */
    public boolean isInCombat(Player player) {
        if (!antylogoutEnabled || player == null) return false;

        try {
            Object result = isPlayerTaggedMethod.invoke(null, player);
            boolean inCombat = result instanceof Boolean && (Boolean) result;

            // ✅ DEBUG LOG
            if (inCombat) {
                plugin.getLogger().info("[CombatIntegration] Gracz " + player.getName() + " jest w walce!");
            }

            return inCombat;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad sprawdzania combat dla " + player.getName() + ": " + e.getMessage());
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

    /**
     * ✅ NOWA METODA: Taguje gracza (rozpoczyna combat tag).
     * 
     * @param victim Gracz który dostaje tag
     * @param attacker Gracz który atakuje (lub null jeśli Hydroklatka)
     * @return true jeśli tagowanie się powiodło
     */
    public boolean tagPlayer(Player victim, Player attacker) {
        if (!antylogoutEnabled || victim == null) return false;

        // Jeśli metoda nie istnieje w API - skip
        if (tagPlayerMethod == null) {
            return false;
        }

        try {
            tagPlayerMethod.invoke(null, victim, attacker);
            plugin.getLogger().info("[CombatIntegration] Ztagowano gracza " + victim.getName() +
                    (attacker != null ? " przez " + attacker.getName() : " przez Hydroklatke"));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[CombatIntegration] Blad tagowania gracza " + victim.getName() + ": " + e.getMessage());
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
