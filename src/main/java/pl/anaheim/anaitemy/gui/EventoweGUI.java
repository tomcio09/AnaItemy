package pl.anaheim.anaitemy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.*;

import java.util.*;

public class EventoweGUI {

    public static final String GUI_TITLE_PREFIX = "Przedmioty z wydarzeń";

    public enum Category { ALL, STALE, ZUZYWALNE, ZBROJE }

    private static final Map<UUID, GUIState> playerStates = new HashMap<>();
    private static final int ITEMS_PER_PAGE = 36;
    private static final int FILTER_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int PREV_PAGE_SLOT = 45;

    public static void open(Player player, AnaItemy plugin) {
        open(player, plugin, Category.ALL, 1);
    }

    public static void open(Player player, AnaItemy plugin, Category category, int page) {
        List<ItemStack> items = getItemsForCategory(category, plugin);

        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        playerStates.put(player.getUniqueId(), new GUIState(category, page));

        String titleText = "&8Przedmioty z wydarzeń &7(" + page + "/" + totalPages + ")";
        Component title = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(titleText).decoration(TextDecoration.ITALIC, false);

        Inventory gui = Bukkit.createInventory(null, 54, title);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= items.size()) break;
            gui.setItem(i, items.get(itemIndex));
        }

        gui.setItem(FILTER_SLOT, createFilterItem(category));

        if (page < totalPages) gui.setItem(NEXT_PAGE_SLOT, createNextPageItem());
        if (page > 1) gui.setItem(PREV_PAGE_SLOT, createPrevPageItem());

        player.openInventory(gui);
    }

    private static List<ItemStack> getItemsForCategory(Category category, AnaItemy plugin) {
        List<ItemStack> items = new ArrayList<>();
        int maxKills = plugin.getItemsConfig().getExcaliburMaxKills();

        switch (category) {
            case ALL -> {
                items.addAll(getStaleItems(maxKills));
                items.addAll(getZuzywalneItems());
                items.addAll(getZbrojeItems());
            }
            case STALE -> items.addAll(getStaleItems(maxKills));
            case ZUZYWALNE -> items.addAll(getZuzywalneItems());
            case ZBROJE -> items.addAll(getZbrojeItems());
        }

        return items;
    }

    private static List<ItemStack> getStaleItems(int maxKills) {
        List<ItemStack> items = new ArrayList<>();
        items.add(TotemUlaskawienia.create());
        items.add(Excalibur.create(maxKills));
        items.add(HydroKlatka.create());
        items.add(RozdzkailuzjonistyItem.create());
        items.add(WedkaNielotaItem.create());
        items.add(SakiewkaDropu.create());
        items.add(WzmocnianaElytra.create());
        items.add(BlokWidmoItem.create());
        items.add(SiekieraGrinchaItem.create());
        items.add(HydroTrojzabItem.create());
        items.add(CudownaLatarniaItem.create());
        items.add(RogJednorozcaItem.create());
        items.add(BoskiToporItem.create());
        items.add(SuperMarchewkaItem.create());
        items.add(LopataGrinchaItem.create());
        items.add(RozgaItem.create());
        items.add(ArcusMagnusItem.create());
        items.add(KroliczyMieczItem.create());
        items.add(PiekielnyMieczItem.create());
        items.add(SmoczyMieczItem.create());
        items.add(KosaItem.create());
        items.add(LukKupidynaItem.create());
        items.add(MarchewkowyMieczItem.create());
        items.add(MarchewkowaKuszaItem.create());
        items.add(WedkaSurferkaItem.create());
        items.add(ZatrutyOlowekItem.create());
        items.add(KoronaAnarchiiItem.create());
        items.add(PiekielnaTarczaItem.create());
        items.add(RozaKupidynaItem.create());
        items.add(LizakItem.create());
        items.add(KukurydzaItem.create());
        return items;
    }

    private static List<ItemStack> getZuzywalneItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(KostkaRubikaItem.create());
        items.add(SniezkaZamianyItem.create());
        return items;
    }

    private static List<ItemStack> getZbrojeItems() {
        return new ArrayList<>();
    }

    private static ItemStack createFilterItem(Category currentCategory) {
        ItemStack hopper = new ItemStack(Material.HOPPER);
        ItemMeta meta = hopper.getItemMeta();
        meta.displayName(colorize("&6Filtracja"));

        List<Component> lore = new ArrayList<>();
        lore.add(colorize(" &8» &7Wyświetlaj według&8:"));
        lore.add(colorize(currentCategory == Category.ALL ? " &7 ➤ &e&nWszystkie" : " &7 ➤ &fWszystkie"));
        lore.add(colorize(currentCategory == Category.STALE ? " &7 ➤ &e&nStałe" : " &7 ➤ &fStałe"));
        lore.add(colorize(currentCategory == Category.ZUZYWALNE ? " &7 ➤ &e&nZużywalne" : " &7 ➤ &fZużywalne"));
        lore.add(colorize(currentCategory == Category.ZBROJE ? " &7 ➤ &e&nZbroje" : " &7 ➤ &fZbroje"));
        lore.add(colorize(""));
        lore.add(colorize(" &8» &aKliknij, aby &2przełączyć&a!"));

        meta.lore(lore);
        hopper.setItemMeta(meta);
        return hopper;
    }

    private static ItemStack createNextPageItem() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colorize("&aNastępna strona"));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createPrevPageItem() {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(colorize("&cPoprzednia strona"));
        item.setItemMeta(meta);
        return item;
    }

    public static GUIState getState(Player player) { return playerStates.get(player.getUniqueId()); }
    public static void removeState(Player player) { playerStates.remove(player.getUniqueId()); }

    public static Category getNextCategory(Category current) {
        return switch (current) {
            case ALL -> Category.STALE;
            case STALE -> Category.ZUZYWALNE;
            case ZUZYWALNE -> Category.ZBROJE;
            case ZBROJE -> Category.ALL;
        };
    }

    public static boolean isGUITitle(String plainTitle) {
        return plainTitle.startsWith("Przedmioty z wydarzeń");
    }

    private static Component colorize(String text) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    public static class GUIState {
        private final Category category;
        private final int page;
        public GUIState(Category category, int page) { this.category = category; this.page = page; }
        public Category getCategory() { return category; }
        public int getPage() { return page; }
    }
}
