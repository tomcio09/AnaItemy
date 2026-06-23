package pl.anaheim.anaitemy.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import pl.anaheim.anaitemy.AnaItemy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * ✅ Klasa do zarządzania itemami w sakiewce (NBT storage).
 */
public class SakiewkaData {

    private static final NamespacedKey ITEMS_KEY = new NamespacedKey(AnaItemy.getInstance(), "sakiewka_items");

    /**
     * Zapisuje listę itemów do sakiewki (NBT).
     */
    public static void saveItems(ItemStack sakiewka, List<ItemStack> items) {
        if (sakiewka == null || !sakiewka.hasItemMeta()) return;

        try {
            // Serializuj listę itemów do byte[]
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Zapisz liczbę itemów
            dataOutput.writeInt(items.size());

            // Zapisz każdy item
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            byte[] serialized = outputStream.toByteArray();

            // Zapisz do NBT
            ItemMeta meta = sakiewka.getItemMeta();
            meta.getPersistentDataContainer().set(ITEMS_KEY, PersistentDataType.BYTE_ARRAY, serialized);
            sakiewka.setItemMeta(meta);

        } catch (Exception e) {
            AnaItemy.getInstance().getLogger().severe("Błąd podczas zapisywania itemów do sakiewki: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Odczytuje listę itemów z sakiewki (NBT).
     */
    public static List<ItemStack> loadItems(ItemStack sakiewka) {
        List<ItemStack> items = new ArrayList<>();
        if (sakiewka == null || !sakiewka.hasItemMeta()) return items;

        ItemMeta meta = sakiewka.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();

        if (!container.has(ITEMS_KEY, PersistentDataType.BYTE_ARRAY)) {
            return items; // Pusta sakiewka
        }

        try {
            byte[] serialized = container.get(ITEMS_KEY, PersistentDataType.BYTE_ARRAY);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(serialized);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int size = dataInput.readInt();

            for (int i = 0; i < size; i++) {
                ItemStack item = (ItemStack) dataInput.readObject();
                if (item != null) {
                    items.add(item);
                }
            }

            dataInput.close();

        } catch (Exception e) {
            AnaItemy.getInstance().getLogger().severe("Błąd podczas odczytywania itemów z sakiewki: " + e.getMessage());
            e.printStackTrace();
        }

        return items;
    }

    /**
     * Dodaje itemy do sakiewki (max 45 slotów).
     * Zwraca itemy które się nie zmieściły.
     */
    public static List<ItemStack> addItems(ItemStack sakiewka, List<ItemStack> newItems) {
        List<ItemStack> current = loadItems(sakiewka);
        List<ItemStack> overflow = new ArrayList<>();

        for (ItemStack newItem : newItems) {
            if (current.size() >= 45) {
                // Brak miejsca - dodaj do overflow
                overflow.add(newItem);
            } else {
                // Dodaj do sakiewki (zawsze nowy slot - bez stackowania)
                current.add(newItem);
            }
        }

        saveItems(sakiewka, current);
        return overflow;
    }

    /**
     * Usuwa wszystkie itemy z sakiewki i zwraca je.
     */
    public static List<ItemStack> removeAllItems(ItemStack sakiewka) {
        List<ItemStack> items = loadItems(sakiewka);
        saveItems(sakiewka, new ArrayList<>()); // Wyczyść sakiewkę
        return items;
    }

    /**
     * Usuwa item ze slotu i zwraca go.
     */
    public static ItemStack removeItem(ItemStack sakiewka, int slot) {
        if (slot < 0 || slot >= 45) return null;

        List<ItemStack> items = loadItems(sakiewka);
        if (slot >= items.size()) return null;

        ItemStack removed = items.get(slot);
        items.set(slot, null); // Zostaw null (pusty slot)

        saveItems(sakiewka, items);
        return removed;
    }

    /**
     * Sprawdza ile wolnych slotów jest w sakiewce.
     */
    public static int getFreeSlots(ItemStack sakiewka) {
        List<ItemStack> items = loadItems(sakiewka);
        int occupied = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                occupied++;
            }
        }
        return 45 - occupied;
    }

    /**
     * Sprawdza czy sakiewka jest pusta.
     */
    public static boolean isEmpty(ItemStack sakiewka) {
        List<ItemStack> items = loadItems(sakiewka);
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
