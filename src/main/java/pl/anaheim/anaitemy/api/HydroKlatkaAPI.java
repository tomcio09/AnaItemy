package pl.anaheim.anaitemy.api;

import org.bukkit.entity.Player;

/**
 * ============================================================
 * API HydroKlatka - AnaItemy Plugin
 * ============================================================
 *
 * Pozwala innym pluginom zarządzać cooldownem HydroKlatki.
 *
 * ============================================================
 * PROMPT DLA INNYCH AI:
 * ============================================================
 *
 * Plugin "AnaItemy" posiada API do zarządzania cooldownem itemu HydroKlatka.
 * API jest dostępne przez klasę HydroKlatkaAPI.
 *
 * JAK UZYSKAĆ INSTANCJĘ API:
 *
 *   // W onEnable() swojego pluginu:
 *   Plugin anaItemy = getServer().getPluginManager().getPlugin("AnaItemy");
 *   if (anaItemy != null && anaItemy.isEnabled()) {
 *       HydroKlatkaAPI api = ((AnaItemy) anaItemy).getHydroKlatkaAPI();
 *       // Gotowe do użycia
 *   }
 *
 * DOSTĘPNE METODY:
 *
 *   // Ustawia cooldown na X sekund (nadpisuje istniejący jeśli jest krótszy)
 *   api.setCooldown(player, 15);
 *
 *   // Dodaje X sekund do istniejącego cooldownu
 *   api.addCooldown(player, 10);
 *
 *   // Usuwa cooldown całkowicie
 *   api.removeCooldown(player);
 *
 *   // Sprawdza czy gracz ma aktywny cooldown
 *   boolean hasCooldown = api.hasCooldown(player);
 *
 *   // Pobiera pozostały czas cooldownu w sekundach
 *   long remaining = api.getRemainingCooldown(player);
 *
 *   // Sprawdza czy gracz jest uwięziony w klatce
 *   boolean trapped = api.isPlayerTrapped(player);
 *
 *   // Uwalnia gracza z klatki
 *   api.freePlayer(player);
 *
 * PRZYKŁAD UŻYCIA - Nałożenie 15s cooldownu po śmierci:
 *
 *   @EventHandler
 *   public void onDeath(PlayerDeathEvent event) {
 *       Player player = event.getEntity();
 *       HydroKlatkaAPI api = ((AnaItemy) anaItemyPlugin).getHydroKlatkaAPI();
 *       api.setCooldown(player, 15);
 *   }
 *
 * PRZYKŁAD UŻYCIA - Blokowanie użycia na arenie:
 *
 *   @EventHandler
 *   public void onArenaJoin(ArenaJoinEvent event) {
 *       Player player = event.getPlayer();
 *       HydroKlatkaAPI api = ((AnaItemy) anaItemyPlugin).getHydroKlatkaAPI();
 *       // Cooldown na 999999s = praktycznie zablokowane
 *       api.setCooldown(player, 999999);
 *   }
 *
 *   @EventHandler
 *   public void onArenaLeave(ArenaLeaveEvent event) {
 *       Player player = event.getPlayer();
 *       HydroKlatkaAPI api = ((AnaItemy) anaItemyPlugin).getHydroKlatkaAPI();
 *       api.removeCooldown(player);
 *   }
 *
 * ZALEŻNOŚĆ W PLUGIN.YML INNEGO PLUGINU:
 *
 *   depend: [AnaItemy]
 *   # lub
 *   softdepend: [AnaItemy]
 *
 * ============================================================
 */
public class HydroKlatkaAPI {

    private final pl.anaheim.anaitemy.AnaItemy plugin;

    public HydroKlatkaAPI(pl.anaheim.anaitemy.AnaItemy plugin) {
        this.plugin = plugin;
    }

    /**
     * Ustawia cooldown na HydroKlatkę dla gracza.
     * Jeśli gracz ma już cooldown, nadpisuje go TYLKO jeśli nowy jest dłuższy.
     *
     * @param player  gracz
     * @param seconds czas cooldownu w sekundach
     */
    public void setCooldown(Player player, long seconds) {
        if (player == null || seconds <= 0) return;

        long currentRemaining = getRemainingCooldown(player);

        // Nadpisz tylko jeśli nowy cooldown jest dłuższy
        if (seconds > currentRemaining) {
            plugin.getHydroKlatkaManager().setExternalCooldown(player, seconds);
        }
    }

    /**
     * Ustawia cooldown na HydroKlatkę dla gracza.
     * ZAWSZE nadpisuje istniejący cooldown, nawet jeśli nowy jest krótszy.
     *
     * @param player  gracz
     * @param seconds czas cooldownu w sekundach
     */
    public void setCooldownForce(Player player, long seconds) {
        if (player == null || seconds <= 0) return;
        plugin.getHydroKlatkaManager().setExternalCooldown(player, seconds);
    }

    /**
     * Dodaje czas do istniejącego cooldownu.
     * Jeśli gracz nie ma cooldownu, ustawia nowy.
     *
     * @param player  gracz
     * @param seconds ile sekund dodać
     */
    public void addCooldown(Player player, long seconds) {
        if (player == null || seconds <= 0) return;

        long currentRemaining = getRemainingCooldown(player);
        long newTotal = currentRemaining + seconds;

        plugin.getHydroKlatkaManager().setExternalCooldown(player, newTotal);
    }

    /**
     * Usuwa cooldown HydroKlatki dla gracza.
     *
     * @param player gracz
     */
    public void removeCooldown(Player player) {
        if (player == null) return;
        plugin.getHydroKlatkaManager().resetCooldown(player);
        plugin.getHydroKlatkaManager().stopCooldownDisplay(player);
    }

    /**
     * Sprawdza czy gracz ma aktywny cooldown na HydroKlatkę.
     *
     * @param player gracz
     * @return true jeśli gracz ma cooldown
     */
    public boolean hasCooldown(Player player) {
        if (player == null) return false;
        return plugin.getHydroKlatkaManager().isPlayerOnCooldown(player);
    }

    /**
     * Pobiera pozostały czas cooldownu w sekundach.
     *
     * @param player gracz
     * @return pozostały czas w sekundach, 0 jeśli brak cooldownu
     */
    public long getRemainingCooldown(Player player) {
        if (player == null) return 0;
        return plugin.getHydroKlatkaManager().getPlayerCooldownRemaining(player);
    }

    /**
     * Sprawdza czy gracz jest uwięziony w aktywnej HydroKlatce.
     *
     * @param player gracz
     * @return true jeśli gracz jest w klatce
     */
    public boolean isPlayerTrapped(Player player) {
        if (player == null) return false;
        return plugin.getHydroKlatkaManager().getKlatkaForPlayer(player) != null;
    }

    /**
     * Uwalnia gracza z HydroKlatki (jeśli jest uwięziony).
     *
     * @param player gracz
     */
    public void freePlayer(Player player) {
        if (player == null) return;
        plugin.getHydroKlatkaManager().removePlayerFromKlatka(player);
    }
}
