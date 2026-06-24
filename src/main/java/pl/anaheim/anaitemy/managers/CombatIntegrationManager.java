package pl.anaheim.anaitemy.managers;

import org.bukkit.entity.Player;
import pl.anaheim.anaitemy.AnaItemy;

import java.util.UUID;

/**
 * ✅ Manager integracji z pluginem walki (Antylogout).
 * Obsługuje:
 * - Sprawdzanie czy gracz jest w walce
 * - Sprawdzanie czy śmierć była przez wylogowanie
 * - Sprawdzanie czy śmierć była w walce
 * - Pobieranie UUID killera
 */
public class CombatIntegrationManager {

    private final AnaItemy plugin;
    private final boolean antylogoutEnabled;

    public CombatIntegrationManager(AnaItemy plugin) {
        this.plugin = plugin;

        boolean pluginFound = plugin.getServer().getPluginManager().isPluginEnabled("Antylogout");
        boolean configEnabled = plugin.getItemsConfig().isCombatIntegrationEnabled();

        this.antylogoutEnabled = pluginFound && configEnabled;

        if (antylogoutEnabled) {
            plugin.getLogger().info("[CombatIntegration] Antylogout wykryty - integracja wlaczona!");
        } else if (!pluginFound) {
            plugin.getLogger().warning("[CombatIntegration] Antylogout nie znaleziony - integracja wylaczona!");
        } else {
            plugin.getLogger().info("[CombatIntegration] Integracja wylaczona w configu.");
        }
    }

    /**
     * Sprawdza czy gracz jest aktualnie w walce.
     */
    public boolean isInCombat(Player player) {
        if (!antylogoutEnabled) return false;

        try {
            return pl.anacode.antylogout.api.AntylogoutAPI.isPlayerTagged(player);
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
            return pl.anacode.antylogout.api.AntylogoutAPI.wasLogoutDeath(playerUUID);
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
            return pl.anacode.antylogout.api.AntylogoutAPI.wasCombatDeath(playerUUID);
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
            return pl.anacode.antylogout.api.AntylogoutAPI.getKillerOf(victimUUID);
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
            return pl.anacode.antylogout.api.AntylogoutAPI.isRestarting();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return antylogoutEnabled;
    }
}
