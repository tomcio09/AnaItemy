package pl.anaheim.anaitemy.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import pl.anaheim.anaitemy.AnaItemy;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnderchestManager {

    private final AnaItemy plugin;
    private final File dataFolder;
    private final Map<UUID, Integer> expansionLevels = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack[]> cachedContents = new ConcurrentHashMap<>();

    private static final int BASE_SIZE = 27; // 3 rzędy
    private static final int SLOTS_PER_EXPANSION = 9; // 1 rząd
    private static final int MAX_EXPANSIONS = 3; // max 3 dodatkowe rzędy = 6 rzędów łącznie

    public EnderchestManager(AnaItemy plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "enderchests");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    // ==================== EXPANSION ====================

    public int getExpansionLevel(UUID playerId) {
        if (expansionLevels.containsKey(playerId)) return expansionLevels.get(playerId);

        // Załaduj z pliku
        File file = getPlayerFile(playerId);
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            int level = config.getInt("expansion-level", 0);
            expansionLevels.put(playerId, level);
            return level;
        }

        return 0;
    }

    public boolean canExpand(UUID playerId) {
        return getExpansionLevel(playerId) < MAX_EXPANSIONS;
    }

    public boolean expand(Player player) {
        UUID playerId = player.getUniqueId();
        int currentLevel = getExpansionLevel(playerId);

        if (currentLevel >= MAX_EXPANSIONS) return false;

        int newLevel = currentLevel + 1;
        expansionLevels.put(playerId, newLevel);

        // Przenieś itemy ze starego rozmiaru do nowego
        ItemStack[] currentItems = loadContents(playerId);
        int newSize = getSize(playerId);
        ItemStack[] newItems = new ItemStack[newSize];

        for (int i = 0; i < currentItems.length && i < newSize; i++) {
            newItems[i] = currentItems[i];
        }

        cachedContents.put(playerId, newItems);
        savePlayerData(playerId);

        return true;
    }

    public int getSize(UUID playerId) {
        return BASE_SIZE + (getExpansionLevel(playerId) * SLOTS_PER_EXPANSION);
    }

    // ==================== OTWIERANIE EC ====================

    public void openEnderchest(Player player) {
        openEnderchest(player, player.getUniqueId(), player.getName());
    }

    public void openEnderchest(Player viewer, UUID ownerId, String ownerName) {
        int size = getSize(ownerId);
        ItemStack[] contents = loadContents(ownerId);

        String title = "&8Enderchest " + ownerName;
        Component titleComp = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(title);

        Inventory inv = Bukkit.createInventory(null, size, titleComp);

        for (int i = 0; i < contents.length && i < size; i++) {
            if (contents[i] != null) inv.setItem(i, contents[i]);
        }

        viewer.openInventory(inv);
    }

    // ==================== ZAPIS/ODCZYT ====================

    public ItemStack[] loadContents(UUID playerId) {
        if (cachedContents.containsKey(playerId)) {
            return cachedContents.get(playerId);
        }

        int size = getSize(playerId);
        ItemStack[] items = new ItemStack[size];

        File file = getPlayerFile(playerId);
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            int savedLevel = config.getInt("expansion-level", 0);
            expansionLevels.put(playerId, savedLevel);

            // Przelicz rozmiar po załadowaniu poziomu
            size = getSize(playerId);
            items = new ItemStack[size];

            if (config.contains("contents")) {
                for (String key : config.getConfigurationSection("contents").getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        if (slot < size) {
                            items[slot] = config.getItemStack("contents." + key);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        // ✅ Załaduj też vanilla enderchest jeśli nie mamy jeszcze danych
        if (!file.exists()) {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) {
                Inventory vanillaEC = online.getEnderChest();
                for (int i = 0; i < Math.min(vanillaEC.getSize(), size); i++) {
                    items[i] = vanillaEC.getItem(i);
                }
            }
        }

        cachedContents.put(playerId, items);
        return items;
    }

    public void saveContents(UUID playerId, ItemStack[] contents) {
        cachedContents.put(playerId, contents);
        savePlayerData(playerId);
    }

    public void saveFromInventory(UUID playerId, Inventory inventory) {
        int size = getSize(playerId);
        ItemStack[] items = new ItemStack[size];

        for (int i = 0; i < Math.min(inventory.getSize(), size); i++) {
            items[i] = inventory.getItem(i);
        }

        saveContents(playerId, items);
    }

    private void savePlayerData(UUID playerId) {
        File file = getPlayerFile(playerId);
        FileConfiguration config = new YamlConfiguration();

        config.set("expansion-level", getExpansionLevel(playerId));

        ItemStack[] contents = cachedContents.get(playerId);
        if (contents != null) {
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null && !contents[i].getType().isAir()) {
                    config.set("contents." + i, contents[i]);
                }
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[Enderchest] Błąd zapisu dla " + playerId + ": " + e.getMessage());
        }
    }

    private File getPlayerFile(UUID playerId) {
        return new File(dataFolder, playerId.toString() + ".yml");
    }

    // ==================== TYTUŁ EC ====================

    public boolean isEnderchestTitle(String plainTitle) {
        return plainTitle.startsWith("Enderchest ");
    }

    public UUID getOwnerFromTitle(String plainTitle) {
        // Tytuł: "Enderchest NickGracza"
        if (!plainTitle.startsWith("Enderchest ")) return null;
        String name = plainTitle.substring("Enderchest ".length());

        // Szukaj po nicku w cache
        for (Map.Entry<UUID, ItemStack[]> entry : cachedContents.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.getName().equals(name)) return entry.getKey();
        }

        // Szukaj offline
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op.hasPlayedBefore() || op.isOnline()) return op.getUniqueId();

        return null;
    }

    // ==================== CLEANUP ====================

    public void saveAll() {
        for (UUID playerId : cachedContents.keySet()) {
            savePlayerData(playerId);
        }
    }

    public void onPlayerJoin(Player player) {
        // Załaduj dane gracza
        loadContents(player.getUniqueId());
    }

    public void onPlayerQuit(Player player) {
        // Zapisz dane gracza
        UUID id = player.getUniqueId();
        if (cachedContents.containsKey(id)) {
            savePlayerData(id);
        }
    }

    public void cleanup() {
        saveAll();
        cachedContents.clear();
        expansionLevels.clear();
    }
}
