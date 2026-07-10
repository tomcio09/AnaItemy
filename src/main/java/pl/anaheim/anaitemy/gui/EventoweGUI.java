package pl.anaheim.anaitemy.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import pl.anaheim.anaitemy.items.AnarchicznySetItem;
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

    public static void open(Player player, AnaItemy plugin) { open(player, plugin, Category.ALL, 1); }

    public static void open(Player player, AnaItemy plugin, Category category, int page) {
        List<ItemStack> items = getItemsForCategory(category, plugin);
        int totalPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE));
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;
        playerStates.put(player.getUniqueId(), new GUIState(category, page));
        Component title = LegacyComponentSerializer.legacyAmpersand()
                .deserialize("&8Przedmioty z wydarzeń &7(" + page + "/" + totalPages + ")")
                .decoration(TextDecoration.ITALIC, false);
        Inventory gui = Bukkit.createInventory(null, 54, title);
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int idx = startIndex + i;
            if (idx >= items.size()) break;
            gui.setItem(i, items.get(idx));
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
            case ALL -> { items.addAll(getStaleItems(maxKills)); items.addAll(getZuzywalneItems()); items.addAll(getZbrojeItems()); }
            case STALE -> items.addAll(getStaleItems(maxKills));
            case ZUZYWALNE -> items.addAll(getZuzywalneItems());
            case ZBROJE -> items.addAll(getZbrojeItems());
        }
        return items;
    }

    private static List<ItemStack> getStaleItems(int maxKills) {
        List<ItemStack> i = new ArrayList<>();
        i.add(TotemUlaskawienia.create()); i.add(Excalibur.create(maxKills)); i.add(HydroKlatka.create());
        i.add(RozdzkailuzjonistyItem.create()); i.add(WedkaNielotaItem.create()); i.add(SakiewkaDropu.create());
        i.add(WzmocnianaElytra.create()); i.add(BlokWidmoItem.create()); i.add(SiekieraGrinchaItem.create());
        i.add(HydroTrojzabItem.create()); i.add(CudownaLatarniaItem.create()); i.add(RogJednorozcaItem.create());
        i.add(BoskiToporItem.create()); i.add(SuperMarchewkaItem.create()); i.add(LopataGrinchaItem.create());
        i.add(RozgaItem.create()); i.add(ArcusMagnusItem.create()); i.add(KroliczyMieczItem.create());
        i.add(PiekielnyMieczItem.create()); i.add(SmoczyMieczItem.create()); i.add(KosaItem.create());
        i.add(LukKupidynaItem.create()); i.add(MarchewkowyMieczItem.create()); i.add(MarchewkowaKuszaItem.create());
        i.add(WedkaSurferkaItem.create()); i.add(ZatrutyOlowekItem.create()); i.add(KoronaAnarchiiItem.create());
        i.add(PiekielnaTarczaItem.create()); i.add(RozaKupidynaItem.create()); i.add(LizakItem.create());
        i.add(KukurydzaItem.create());
        return i;
    }

    private static List<ItemStack> getZuzywalneItems() {
        List<ItemStack> i = new ArrayList<>();
        i.add(KostkaRubikaItem.create()); i.add(SniezkaZamianyItem.create()); i.add(TurbotrapItem.create());
        i.add(KrewWampiraItem.create()); i.add(BalonikItem.create()); i.add(WataCukrowaItem.create());
        i.add(PiernikItem.create()); i.add(ZlamaneSerceItem.create()); i.add(WampirzeJablkoItem.create());
        i.add(CiepleMlekoItem.create()); i.add(ParawanItem.create()); i.add(SplesnialaKanapkaItem.create());
        i.add(LeweJajkoItem.create()); i.add(PrzeterminowanyTrunekItem.create()); i.add(BombardaMaximaItem.create());
        i.add(DynamitItem.create()); i.add(KamienKowalskiItem.create()); i.add(PrzepustkaNeteruItem.create());
        i.add(CreeperZmutowanyItem.create()); i.add(OlafItem.create()); i.add(RozszerzenieECItem.create());
        return i;
    }

    private static List<ItemStack> getZbrojeItems() {
        List<ItemStack> i = new ArrayList<>();
        // Set 2
        i.add(AnarchicznySetItem.createHelm2());
        i.add(AnarchicznySetItem.createKlata2());
        i.add(AnarchicznySetItem.createSpodnie2());
        i.add(AnarchicznySetItem.createButy2());
        // Set 1
        i.add(AnarchicznySetItem.createHelm1());
        i.add(AnarchicznySetItem.createKlata1());
        i.add(AnarchicznySetItem.createSpodnie1());
        i.add(AnarchicznySetItem.createButy1());
        // Narzędzia
        i.add(AnarchicznySetItem.createKilof());
        i.add(AnarchicznySetItem.createMiecz());
        i.add(AnarchicznySetItem.createLuk());
        return i;
    }

    private static ItemStack createFilterItem(Category c) {
        ItemStack h = new ItemStack(Material.HOPPER); ItemMeta m = h.getItemMeta();
        m.displayName(col("&6Filtracja"));
        List<Component> l = new ArrayList<>();
        l.add(col(" &8» &7Wyświetlaj według&8:"));
        l.add(col(c == Category.ALL ? " &7 ➤ &e&nWszystkie" : " &7 ➤ &fWszystkie"));
        l.add(col(c == Category.STALE ? " &7 ➤ &e&nStałe" : " &7 ➤ &fStałe"));
        l.add(col(c == Category.ZUZYWALNE ? " &7 ➤ &e&nZużywalne" : " &7 ➤ &fZużywalne"));
        l.add(col(c == Category.ZBROJE ? " &7 ➤ &e&nZbroje" : " &7 ➤ &fZbroje"));
        l.add(col("")); l.add(col(" &8» &aKliknij, aby &2przełączyć&a!"));
        m.lore(l); h.setItemMeta(m); return h;
    }

    private static ItemStack createNextPageItem() {
        ItemStack i = new ItemStack(Material.LIME_DYE); ItemMeta m = i.getItemMeta();
        m.displayName(col("&aNastępna strona")); i.setItemMeta(m); return i;
    }

    private static ItemStack createPrevPageItem() {
        ItemStack i = new ItemStack(Material.RED_DYE); ItemMeta m = i.getItemMeta();
        m.displayName(col("&cPoprzednia strona")); i.setItemMeta(m); return i;
    }

    public static GUIState getState(Player p) { return playerStates.get(p.getUniqueId()); }
    public static void removeState(Player p) { playerStates.remove(p.getUniqueId()); }
    public static Category getNextCategory(Category c) {
        return switch (c) { case ALL -> Category.STALE; case STALE -> Category.ZUZYWALNE; case ZUZYWALNE -> Category.ZBROJE; case ZBROJE -> Category.ALL; };
    }
    public static boolean isGUITitle(String t) { return t.startsWith("Przedmioty z wydarzeń"); }
    private static Component col(String t) { return LegacyComponentSerializer.legacyAmpersand().deserialize(t).decoration(TextDecoration.ITALIC, false); }

    public static class GUIState {
        private final Category category; private final int page;
        public GUIState(Category c, int p) { this.category = c; this.page = p; }
        public Category getCategory() { return category; }
        public int getPage() { return page; }
    }
}
