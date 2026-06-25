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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemyEventoweCommand implements CommandExecutor, TabCompleter {

    private final AnaItemy plugin;

    private static final List<String> ITEM_IDS = Arrays.asList(
            "totem", "excalibur", "hydroklatka", "rozdzka", "wedka", "sakiewka",
            "elytra", "blokwidmo", "siekiera", "hydrotrident", "latarnia", "rog",
            "topor", "marchewka", "lopata", "rozga"
    );

    public ItemyEventoweCommand(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (sender instanceof Player player && !player.isOp()) {
            player.sendMessage(color("&cNie masz uprawnień do tej komendy!"));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Ta komenda jest tylko dla graczy!"); return true; }
            EventoweGUI.open(player, plugin);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { handleReload(sender); return true; }
        if (args.length == 2 && args[0].equalsIgnoreCase("kills")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Ta komenda jest tylko dla graczy!"); return true; }
            handleKillsCommand(player, args[1]); return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) { handleGiveCommand(sender, args[1], args[2], args[3]); return true; }
        if (args.length == 3 && args[0].equalsIgnoreCase("cooldown") && args[1].equalsIgnoreCase("reset")) { handleCooldownReset(sender, args[2]); return true; }
        if (args.length == 3 && args[0].equalsIgnoreCase("klatwa")) { handleKlatwaCommand(sender, args[1], args[2]); return true; }
        if (args.length == 3 && args[0].equalsIgnoreCase("widmo")) { handleWidmoCommand(sender, args[1], args[2]); return true; }

        sendHelp(sender);
        return true;
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig(); plugin.getItemsConfig().reloadConfig();
        sender.sendMessage(color("&aZreloadowano konfigurację &fconfig.yml &ai &fitems.yml&a!"));
    }

    private void handleGiveCommand(CommandSender sender, String itemId, String targetName, String amountStr) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { sender.sendMessage(color("&cGracz &f" + targetName + " &cnie jest online!")); return; }
        int amount;
        try { amount = Integer.parseInt(amountStr); if (amount <= 0) { sender.sendMessage(color("&cIlość musi być większa niż 0!")); return; }
        } catch (NumberFormatException e) { sender.sendMessage(color("&cPodaj poprawną liczbę!")); return; }

        if (itemId.equalsIgnoreCase("sakiewka")) {
            int freeSlots = countFreeSlots(target);
            if (freeSlots < amount) { sender.sendMessage(color("&cGracz &4" + target.getName() + " &cma pełny ekwipunek!")); return; }
            for (int i = 0; i < amount; i++) target.getInventory().addItem(SakiewkaDropu.create());
            sender.sendMessage(color("&aDano &f" + amount + "x &7[" + getItemDisplayName(itemId) + "&7] &agraczowi &f" + target.getName() + "&a!"));
            target.sendMessage(color("&aOtrzymałeś &f" + amount + "x &7[" + getItemDisplayName(itemId) + "&7]&a!"));
            return;
        }
        ItemStack item = createItemById(itemId.toLowerCase());
        if (item == null) { sender.sendMessage(color("&cNieznany item: &f" + itemId)); sender.sendMessage(color("&7Dostępne: &f" + String.join(", ", ITEM_IDS))); return; }
        int freeSlots = countFreeSlots(target);
        if (freeSlots < (int) Math.ceil((double) amount / item.getMaxStackSize())) { sender.sendMessage(color("&cGracz &4" + target.getName() + " &cma pełny ekwipunek!")); return; }
        item.setAmount(amount); target.getInventory().addItem(item);
        sender.sendMessage(color("&aDano &f" + amount + "x &7[" + getItemDisplayName(itemId) + "&7] &agraczowi &f" + target.getName() + "&a!"));
        target.sendMessage(color("&aOtrzymałeś &f" + amount + "x &7[" + getItemDisplayName(itemId) + "&7]&a!"));
    }

    private ItemStack createItemById(String id) {
        int maxKills = plugin.getItemsConfig().getExcaliburMaxKills();
        return switch (id) {
            case "totem" -> TotemUlaskawienia.create();
            case "excalibur" -> Excalibur.create(maxKills);
            case "hydroklatka" -> HydroKlatka.create();
            case "rozdzka" -> RozdzkailuzjonistyItem.create();
            case "wedka" -> WedkaNielotaItem.create();
            case "elytra" -> WzmocnianaElytra.create();
            case "blokwidmo" -> BlokWidmoItem.create();
            case "siekiera" -> SiekieraGrinchaItem.create();
            case "hydrotrident", "hydrotrojzab" -> HydroTrojzabItem.create();
            case "latarnia" -> CudownaLatarniaItem.create();
            case "rog" -> RogJednorozcaItem.create();
            case "topor" -> BoskiToporItem.create();
            case "marchewka" -> SuperMarchewkaItem.create();
            case "lopata" -> LopataGrinchaItem.create();
            case "rozga" -> RozgaItem.create();
            default -> null;
        };
    }

    private String getItemDisplayName(String id) {
        return switch (id.toLowerCase()) {
            case "totem" -> "&5Totem Ułaskawienia";
            case "excalibur" -> "&eExcalibur";
            case "hydroklatka" -> "&3Wyrzutnia Hydro Klatki";
            case "rozdzka" -> "&5Różdżka Iluzjonisty";
            case "wedka" -> "&5Wędka Nielota";
            case "sakiewka" -> "&aSakiewka Dropu";
            case "elytra" -> "&5Wzmocniana Elytra";
            case "blokwidmo" -> "&cBlok Widmo";
            case "siekiera" -> "&2Siekiera Grincha";
            case "hydrotrident", "hydrotrojzab" -> "&3Hydro Trójząb";
            case "latarnia" -> "&dCudowna Latarnia";
            case "rog" -> "&dRóg Jednorożca";
            case "topor" -> "&bBoski Topór";
            case "marchewka" -> "&6Super Marchewka";
            case "lopata" -> "&aŁopata Grincha";
            case "rozga" -> "&6Rózga";
            default -> id;
        };
    }

    private int countFreeSlots(Player player) {
        int free = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) if (slot == null || slot.getType().isAir()) free++;
        return free;
    }

    private void handleKillsCommand(Player player, String killsStr) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!Excalibur.isExcalibur(item)) { player.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageNotHolding())); return; }
        int kills;
        try { kills = Integer.parseInt(killsStr); } catch (NumberFormatException e) { player.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageInvalidNumber())); return; }
        if (kills < 0) { player.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageNegativeNumber())); return; }
        int maxKills = plugin.getItemsConfig().getExcaliburMaxKills();
        if (kills > maxKills) kills = maxKills;
        ItemStack updated = Excalibur.updateKills(item, kills, maxKills);
        player.getInventory().setItemInMainHand(updated);
        player.sendMessage(color(plugin.getItemsConfig().getExcaliburMessageKillsSet().replace("{kills}", String.valueOf(kills))));
    }

    private void handleCooldownReset(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { sender.sendMessage(color("&cGracz &f" + targetName + " &cnie jest online!")); return; }

        plugin.getHydroKlatkaManager().resetCooldown(target);
        target.setCooldown(org.bukkit.Material.BLAZE_ROD, 0);
        plugin.getHydroKlatkaManager().stopCooldownDisplay(target);
        plugin.getRozdzkailuzjonistyManager().resetFangsCooldown(target);
        plugin.getRozdzkailuzjonistyManager().resetVanishCooldown(target);
        plugin.getWedkaNielotaManager().resetCooldown(target);
        plugin.getBlokWidmoManager().resetCooldown(target);
        plugin.getSiekieraGrinchaManager().resetCooldown(target);
        plugin.getHydroTrojzabManager().resetCooldowns(target);
        plugin.getCudownaLatarniaManager().resetCooldown(target);
        plugin.getRogJednorozcaManager().resetCooldown(target);
        plugin.getBoskiToporManager().resetCooldown(target);
        plugin.getSuperMarchewkaManager().resetCooldown(target);
        plugin.getLopataGrinchaManager().resetCooldown(target);

        sender.sendMessage(color("&aZresetowano cooldowny gracza &f" + target.getName() + "&a!"));
        target.sendMessage(color("&aTwoje cooldowny zostały zresetowane przez &f" + sender.getName() + "&a!"));
    }

    private void handleKlatwaCommand(CommandSender sender, String action, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { sender.sendMessage(color("&cGracz &f" + targetName + " &cnie jest online!")); return; }
        WedkaNielotaManager wedkaManager = plugin.getWedkaNielotaManager();
        switch (action.toLowerCase()) {
            case "naloz" -> { wedkaManager.applyCurse(target, null); sender.sendMessage(color("&aNałożono klątwę na gracza &f" + target.getName() + "&a!")); }
            case "zdejmij" -> {
                if (!wedkaManager.hasCurse(target)) { sender.sendMessage(color("&cGracz &f" + target.getName() + " &cnie ma klątwy!")); return; }
                wedkaManager.forceRemoveCurse(target); sender.sendMessage(color("&aZdjęto klątwę z gracza &f" + target.getName() + "&a!"));
                target.sendMessage(color("&aKlątwa została z Ciebie zdjęta!"));
            }
            default -> sender.sendMessage(color("&cUżycie: &f/itemyeventowe klatwa <naloz|zdejmij> <nick>"));
        }
    }

    private void handleWidmoCommand(CommandSender sender, String action, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) { sender.sendMessage(color("&cGracz &f" + targetName + " &cnie jest online!")); return; }
        BlokWidmoManager widmoManager = plugin.getBlokWidmoManager();
        switch (action.toLowerCase()) {
            case "naloz" -> { widmoManager.activate(target, target.getLocation()); sender.sendMessage(color("&aNałożono efekt Bloku Widmo na gracza &f" + target.getName() + "&a!")); }
            case "zdejmij" -> {
                if (!widmoManager.isAffected(target)) { sender.sendMessage(color("&cGracz &f" + target.getName() + " &cnie ma efektu Bloku Widmo!")); return; }
                widmoManager.forceRemoveEffect(target); sender.sendMessage(color("&aZdjęto efekt Bloku Widmo z gracza &f" + target.getName() + "&a!"));
                target.sendMessage(color("&aEfekt Bloku Widmo został z Ciebie zdjęty!"));
            }
            default -> sender.sendMessage(color("&cUżycie: &f/itemyeventowe widmo <naloz|zdejmij> <nick>"));
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&8&m                                    "));
        sender.sendMessage(color("&e&lAnaItemy &7- Dostępne komendy:"));
        sender.sendMessage(color("&8&m                                    "));
        sender.sendMessage(color("&7/itemyeventowe &8- &fotwiera GUI"));
        sender.sendMessage(color("&7/itemyeventowe reload &8- &freładuje konfigurację"));
        sender.sendMessage(color("&7/itemyeventowe give <id> <nick> <ilość> &8- &fdaje item"));
        sender.sendMessage(color("&7/itemyeventowe kills <liczba> &8- &fustawia kille Excalibura"));
        sender.sendMessage(color("&7/itemyeventowe cooldown reset <nick> &8- &fresetuje cooldowny"));
        sender.sendMessage(color("&7/itemyeventowe klatwa naloz <nick> &8- &fnakłada klątwę"));
        sender.sendMessage(color("&7/itemyeventowe klatwa zdejmij <nick> &8- &fzdejmuje klątwę"));
        sender.sendMessage(color("&7/itemyeventowe widmo naloz <nick> &8- &fnakłada efekt widmo"));
        sender.sendMessage(color("&7/itemyeventowe widmo zdejmij <nick> &8- &fzdejmuje efekt widmo"));
        sender.sendMessage(color("&7Dostępne ID itemów: &f" + String.join(", ", ITEM_IDS)));
        sender.sendMessage(color("&8&m                                    "));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) completions.addAll(Arrays.asList("reload", "kills", "give", "cooldown", "klatwa", "widmo"));
        else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "kills" -> completions.add("<liczba>");
                case "give" -> completions.addAll(ITEM_IDS);
                case "cooldown" -> completions.add("reset");
                case "klatwa", "widmo" -> completions.addAll(Arrays.asList("naloz", "zdejmij"));
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "give", "klatwa", "widmo" -> completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
                case "cooldown" -> { if (args[1].equalsIgnoreCase("reset")) completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList())); }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) completions.addAll(Arrays.asList("1", "5", "10", "64"));

        return completions.stream().filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).collect(Collectors.toList());
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text).decoration(TextDecoration.ITALIC, false);
    }
}
