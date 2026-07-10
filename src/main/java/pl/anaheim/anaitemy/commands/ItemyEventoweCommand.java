package pl.anaheim.anaitemy.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.gui.EventoweGUI;
import pl.anaheim.anaitemy.items.*;
import pl.anaheim.anaitemy.managers.*;

import java.util.*;
import java.util.stream.Collectors;

public class ItemyEventoweCommand implements CommandExecutor, TabCompleter {
    private final AnaItemy plugin;
    private static final List<String> ITEM_IDS = Arrays.asList(
            "totem", "excalibur", "hydroklatka", "rozdzka", "wedka", "sakiewka",
            "elytra", "blokwidmo", "siekiera", "hydrotrident", "latarnia", "rog",
            "topor", "marchewka", "lopata", "rozga", "arcus", "kroliczy",
            "piekielny", "smoczy", "kosa", "lukkupidyna", "marchewkowymiecz", "kusza",
            "surferka", "olowek", "korona", "tarcza", "roza", "lizak", "kukurydza",
            "kostka", "sniezka", "turbotrap", "krew", "balonik", "wata",
            "piernik", "zlamaneserce", "wampirze", "mleko", "parawan",
            "kanapka", "lewejajko", "trunek", "bombarda", "dynamit",
            "kamien", "przepustka", "creeper", "olaf", "rozszerzenieec"
    );

    public ItemyEventoweCommand(AnaItemy plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player player && !player.isOp()) { player.sendMessage(color("&cNie masz uprawnień do tej komendy!")); return true; }
        if (args.length == 0) { if (!(sender instanceof Player player)) { sender.sendMessage("Ta komenda jest tylko dla graczy!"); return true; } EventoweGUI.open(player, plugin); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { handleReload(sender); return true; }
        if (args.length == 2 && args[0].equalsIgnoreCase("kills")) { if (!(sender instanceof Player player)) { sender.sendMessage("Ta komenda jest tylko dla graczy!"); return true; } handleKillsCommand(player, args[1]); return true; }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) { handleGiveCommand(sender, args[1], args[2], args[3]); return true; }
        if (args.length == 3 && args[0].equalsIgnoreCase("cooldown") && args[1].equalsIgnoreCase("reset")) { handleCooldownReset(sender, args[2]); return true; }
        if (args.length == 3 && args[0].equalsIgnoreCase("klatwa")) { handleKlatwaCommand(sender, args[1], args[2]); return true; }
        if (args.length == 3 && args[0].equalsIgnoreCase("widmo")) { handleWidmoCommand(sender, args[1], args[2]); return true; }
        sendHelp(sender); return true;
    }

    private void handleReload(CommandSender s) { plugin.reloadConfig(); plugin.getItemsConfig().reloadConfig(); s.sendMessage(color("&aZreloadowano konfigurację &fconfig.yml &ai &fitems.yml&a!")); }

    private void handleGiveCommand(CommandSender s, String id, String name, String amtStr) {
        Player t = Bukkit.getPlayer(name);
        if (t == null) { s.sendMessage(color("&cGracz &f" + name + " &cnie jest online!")); return; }
        int amt; try { amt = Integer.parseInt(amtStr); if (amt <= 0) { s.sendMessage(color("&cIlość musi być większa niż 0!")); return; } } catch (NumberFormatException e) { s.sendMessage(color("&cPodaj poprawną liczbę!")); return; }
        if (id.equalsIgnoreCase("sakiewka")) { int fs = countFreeSlots(t); if (fs < amt) { s.sendMessage(color("&cGracz &4" + t.getName() + " &cma pełny ekwipunek!")); return; } for (int i = 0; i < amt; i++) t.getInventory().addItem(SakiewkaDropu.create()); s.sendMessage(color("&aDano &f" + amt + "x &7[" + getItemDisplayName(id) + "&7] &agraczowi &f" + t.getName() + "&a!")); t.sendMessage(color("&aOtrzymałeś &f" + amt + "x &7[" + getItemDisplayName(id) + "&7]&a!")); return; }
        ItemStack item = createItemById(id.toLowerCase()); if (item == null) { s.sendMessage(color("&cNieznany item: &f" + id)); s.sendMessage(color("&7Dostępne: &f" + String.join(", ", ITEM_IDS))); return; }
        int fs = countFreeSlots(t); if (fs < (int) Math.ceil((double) amt / item.getMaxStackSize())) { s.sendMessage(color("&cGracz &4" + t.getName() + " &cma pełny ekwipunek!")); return; }
        item.setAmount(amt); t.getInventory().addItem(item);
        s.sendMessage(color("&aDano &f" + amt + "x &7[" + getItemDisplayName(id) + "&7] &agraczowi &f" + t.getName() + "&a!")); t.sendMessage(color("&aOtrzymałeś &f" + amt + "x &7[" + getItemDisplayName(id) + "&7]&a!"));
    }

    private ItemStack createItemById(String id) {
        int mk = plugin.getItemsConfig().getExcaliburMaxKills();
        return switch (id) {
            case "totem" -> TotemUlaskawienia.create(); case "excalibur" -> Excalibur.create(mk);
            case "hydroklatka" -> HydroKlatka.create(); case "rozdzka" -> RozdzkailuzjonistyItem.create();
            case "wedka" -> WedkaNielotaItem.create(); case "elytra" -> WzmocnianaElytra.create();
            case "blokwidmo" -> BlokWidmoItem.create(); case "siekiera" -> SiekieraGrinchaItem.create();
            case "hydrotrident", "hydrotrojzab" -> HydroTrojzabItem.create();
            case "latarnia" -> CudownaLatarniaItem.create(); case "rog" -> RogJednorozcaItem.create();
            case "topor" -> BoskiToporItem.create(); case "marchewka" -> SuperMarchewkaItem.create();
            case "lopata" -> LopataGrinchaItem.create(); case "rozga" -> RozgaItem.create();
            case "arcus" -> ArcusMagnusItem.create(); case "kroliczy" -> KroliczyMieczItem.create();
            case "piekielny" -> PiekielnyMieczItem.create(); case "smoczy" -> SmoczyMieczItem.create();
            case "kosa" -> KosaItem.create(); case "lukkupidyna" -> LukKupidynaItem.create();
            case "marchewkowymiecz" -> MarchewkowyMieczItem.create(); case "kusza" -> MarchewkowaKuszaItem.create();
            case "surferka" -> WedkaSurferkaItem.create(); case "olowek" -> ZatrutyOlowekItem.create();
            case "korona" -> KoronaAnarchiiItem.create(); case "tarcza" -> PiekielnaTarczaItem.create();
            case "roza" -> RozaKupidynaItem.create(); case "lizak" -> LizakItem.create();
            case "kukurydza" -> KukurydzaItem.create(); case "kostka" -> KostkaRubikaItem.create();
            case "sniezka" -> SniezkaZamianyItem.create(); case "turbotrap" -> TurbotrapItem.create();
            case "krew" -> KrewWampiraItem.create(); case "balonik" -> BalonikItem.create();
            case "wata" -> WataCukrowaItem.create(); case "piernik" -> PiernikItem.create();
            case "zlamaneserce" -> ZlamaneSerceItem.create(); case "wampirze" -> WampirzeJablkoItem.create();
            case "mleko" -> CiepleMlekoItem.create(); case "parawan" -> ParawanItem.create();
            case "kanapka" -> SplesnialaKanapkaItem.create(); case "lewejajko" -> LeweJajkoItem.create();
            case "trunek" -> PrzeterminowanyTrunekItem.create(); case "bombarda" -> BombardaMaximaItem.create();
            case "dynamit" -> DynamitItem.create(); case "kamien" -> KamienKowalskiItem.create();
            case "przepustka" -> PrzepustkaNeteruItem.create(); case "creeper" -> CreeperZmutowanyItem.create();
            case "olaf" -> OlafItem.create(); case "rozszerzenieec" -> RozszerzenieECItem.create();
            default -> null;
        };
    }

    private String getItemDisplayName(String id) {
        return switch (id.toLowerCase()) {
            case "totem" -> "&5Totem Ułaskawienia"; case "excalibur" -> "&eExcalibur";
            case "hydroklatka" -> "&3Wyrzutnia Hydro Klatki"; case "rozdzka" -> "&5Różdżka Iluzjonisty";
            case "wedka" -> "&5Wędka Nielota"; case "sakiewka" -> "&aSakiewka Dropu";
            case "elytra" -> "&5Wzmocniana Elytra"; case "blokwidmo" -> "&cBlok Widmo";
            case "siekiera" -> "&2Siekiera Grincha"; case "hydrotrident", "hydrotrojzab" -> "&3Hydro Trójząb";
            case "latarnia" -> "&dCudowna Latarnia"; case "rog" -> "&dRóg Jednorożca";
            case "topor" -> "&bBoski Topór"; case "marchewka" -> "&6Super Marchewka";
            case "lopata" -> "&aŁopata Grincha"; case "rozga" -> "&6Rózga";
            case "arcus" -> "&aArcus Magnus"; case "kroliczy" -> "&3Króliczy Miecz";
            case "piekielny" -> "&cPiekielny Miecz"; case "smoczy" -> "&dSmoczy Miecz";
            case "kosa" -> "&8Kosa"; case "lukkupidyna" -> "&4Łuk Kupidyna";
            case "marchewkowymiecz" -> "&6Marchewkowy Miecz"; case "kusza" -> "&6Marchewkowa Kusza";
            case "surferka" -> "&bWędka Surferka"; case "olowek" -> "&aZatruty Ołówek";
            case "korona" -> "&6Korona ANARCHII"; case "tarcza" -> "&6Piekielna Tarcza";
            case "roza" -> "&cRóża Kupidyna"; case "lizak" -> "&dLizak";
            case "kukurydza" -> "&aRozgotowana Kukurydza"; case "kostka" -> "&eKostka Rubika";
            case "sniezka" -> "&fŚnieżka Zamiany"; case "turbotrap" -> "&aTurbo-Trap";
            case "krew" -> "&cKrew Wampira"; case "balonik" -> "&3Balonik z Helem";
            case "wata" -> "&bWata Cukrowa"; case "piernik" -> "&6Piernik";
            case "zlamaneserce" -> "&5Złamane Serce"; case "wampirze" -> "&6Wampirze Jabłko";
            case "mleko" -> "&1Ciepłe Mleko"; case "parawan" -> "&eParawan";
            case "kanapka" -> "&2Spleśniała Kanapka"; case "lewejajko" -> "&eLewe Jajko";
            case "trunek" -> "&2Przeterminowany Trunek"; case "bombarda" -> "&5Bombarda Maxima";
            case "dynamit" -> "&4Dynamit"; case "kamien" -> "&cKamień Kowalski";
            case "przepustka" -> "&4Przepustka Netheru"; case "creeper" -> "&aZmutowany Creeper";
            case "olaf" -> "&bOlaf"; case "rozszerzenieec" -> "&5Rozszerzenie EC";
            default -> id;
        };
    }

    private int countFreeSlots(Player p) { int f = 0; for (ItemStack s : p.getInventory().getStorageContents()) if (s == null || s.getType().isAir()) f++; return f; }

    private void handleKillsCommand(Player p, String ks) {
        ItemStack i = p.getInventory().getItemInMainHand();
        if (!Excalibur.isExcalibur(i)) { p.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageNotHolding())); return; }
        int k; try { k = Integer.parseInt(ks); } catch (NumberFormatException e) { p.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageInvalidNumber())); return; }
        if (k < 0) { p.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageNegativeNumber())); return; }
        int mk = plugin.getItemsConfig().getExcaliburMaxKills(); if (k > mk) k = mk;
        p.getInventory().setItemInMainHand(Excalibur.updateKills(i, k, mk));
        p.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageKillsSet().replace("{kills}", String.valueOf(k))));
    }

    private void handleCooldownReset(CommandSender s, String name) {
        Player t = Bukkit.getPlayer(name);
        if (t == null) { s.sendMessage(color("&cGracz &f" + name + " &cnie jest online!")); return; }
        plugin.getHydroKlatkaManager().resetCooldown(t); t.setCooldown(org.bukkit.Material.BLAZE_ROD, 0); plugin.getHydroKlatkaManager().stopCooldownDisplay(t);
        plugin.getRozdzkailuzjonistyManager().resetFangsCooldown(t); plugin.getRozdzkailuzjonistyManager().resetVanishCooldown(t);
        plugin.getWedkaNielotaManager().resetCooldown(t); plugin.getBlokWidmoManager().resetCooldown(t);
        plugin.getSiekieraGrinchaManager().resetCooldown(t); plugin.getHydroTrojzabManager().resetCooldowns(t);
        plugin.getCudownaLatarniaManager().resetCooldown(t); plugin.getRogJednorozcaManager().resetCooldown(t);
        plugin.getBoskiToporManager().resetCooldown(t); plugin.getSuperMarchewkaManager().resetCooldown(t);
        plugin.getLopataGrinchaManager().resetCooldown(t); plugin.getKroliczyMieczManager().resetCooldown(t);
        plugin.getSmoczyMieczManager().resetCooldown(t); plugin.getOslepienieManager().resetKosaCooldown(t);
        plugin.getOslepienieManager().resetLukCooldown(t); plugin.getMarchewkowyMieczManager().resetCooldown(t);
        plugin.getMarchewkowaKuszaManager().resetCooldown(t); plugin.getWedkaSurferkaManager().resetCooldown(t);
        plugin.getZatrutyOlowekManager().resetCooldown(t); plugin.getKukurydzaManager().resetCooldown(t);
        plugin.getOlafManager().resetCooldowns(t);
        s.sendMessage(color("&aZresetowano cooldowny gracza &f" + t.getName() + "&a!")); t.sendMessage(color("&aTwoje cooldowny zostały zresetowane przez &f" + s.getName() + "&a!"));
    }

    private void handleKlatwaCommand(CommandSender s, String a, String n) {
        Player t = Bukkit.getPlayer(n); if (t == null) { s.sendMessage(color("&cGracz &f" + n + " &cnie jest online!")); return; }
        WedkaNielotaManager m = plugin.getWedkaNielotaManager();
        switch (a.toLowerCase()) { case "naloz" -> { m.applyCurse(t, null); s.sendMessage(color("&aNałożono klątwę na gracza &f" + t.getName() + "&a!")); } case "zdejmij" -> { if (!m.hasCurse(t)) { s.sendMessage(color("&cGracz &f" + t.getName() + " &cnie ma klątwy!")); return; } m.forceRemoveCurse(t); s.sendMessage(color("&aZdjęto klątwę z gracza &f" + t.getName() + "&a!")); t.sendMessage(color("&aKlątwa została z Ciebie zdjęta!")); } default -> s.sendMessage(color("&cUżycie: &f/itemyeventowe klatwa <naloz|zdejmij> <nick>")); }
    }

    private void handleWidmoCommand(CommandSender s, String a, String n) {
        Player t = Bukkit.getPlayer(n); if (t == null) { s.sendMessage(color("&cGracz &f" + n + " &cnie jest online!")); return; }
        BlokWidmoManager m = plugin.getBlokWidmoManager();
        switch (a.toLowerCase()) { case "naloz" -> { m.activate(t, t.getLocation()); s.sendMessage(color("&aNałożono efekt Bloku Widmo na gracza &f" + t.getName() + "&a!")); } case "zdejmij" -> { if (!m.isAffected(t)) { s.sendMessage(color("&cGracz &f" + t.getName() + " &cnie ma efektu Bloku Widmo!")); return; } m.forceRemoveEffect(t); s.sendMessage(color("&aZdjęto efekt Bloku Widmo z gracza &f" + t.getName() + "&a!")); t.sendMessage(color("&aEfekt Bloku Widmo został z Ciebie zdjęty!")); } default -> s.sendMessage(color("&cUżycie: &f/itemyeventowe widmo <naloz|zdejmij> <nick>")); }
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(color("&8&m                                    ")); s.sendMessage(color("&e&lAnaItemy &7- Dostępne komendy:")); s.sendMessage(color("&8&m                                    "));
        s.sendMessage(color("&7/itemyeventowe &8- &fotwiera GUI")); s.sendMessage(color("&7/itemyeventowe reload &8- &freładuje konfigurację"));
        s.sendMessage(color("&7/itemyeventowe give <id> <nick> <ilość> &8- &fdaje item")); s.sendMessage(color("&7/itemyeventowe kills <liczba> &8- &fustawia kille Excalibura"));
        s.sendMessage(color("&7/itemyeventowe cooldown reset <nick> &8- &fresetuje cooldowny")); s.sendMessage(color("&7/itemyeventowe klatwa naloz <nick> &8- &fnakłada klątwę"));
        s.sendMessage(color("&7/itemyeventowe klatwa zdejmij <nick> &8- &fzdejmuje klątwę")); s.sendMessage(color("&7/itemyeventowe widmo naloz <nick> &8- &fnakłada efekt widmo"));
        s.sendMessage(color("&7/itemyeventowe widmo zdejmij <nick> &8- &fzdejmuje efekt widmo"));
        s.sendMessage(color("&7/ec &8- &fotwiera enderchest")); s.sendMessage(color("&7/ecsee <nick> &8- &fpodgląd EC gracza"));
        s.sendMessage(color("&7Dostępne ID itemów: &f" + String.join(", ", ITEM_IDS)));
        s.sendMessage(color("&8&m                                    "));
    }

    @Override public @Nullable List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        List<String> r = new ArrayList<>();
        if (a.length == 1) r.addAll(Arrays.asList("reload", "kills", "give", "cooldown", "klatwa", "widmo"));
        else if (a.length == 2) { switch (a[0].toLowerCase()) { case "kills" -> r.add("<liczba>"); case "give" -> r.addAll(ITEM_IDS); case "cooldown" -> r.add("reset"); case "klatwa", "widmo" -> r.addAll(Arrays.asList("naloz", "zdejmij")); } }
        else if (a.length == 3) { switch (a[0].toLowerCase()) { case "give", "klatwa", "widmo" -> r.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList())); case "cooldown" -> { if (a[1].equalsIgnoreCase("reset")) r.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList())); } } }
        else if (a.length == 4 && a[0].equalsIgnoreCase("give")) r.addAll(Arrays.asList("1", "5", "10", "64"));
        return r.stream().filter(x -> x.toLowerCase().startsWith(a[a.length - 1].toLowerCase())).collect(Collectors.toList());
    }

    private Component color(String t) { return LegacyComponentSerializer.legacyAmpersand().deserialize(t).decoration(TextDecoration.ITALIC, false); }
}
