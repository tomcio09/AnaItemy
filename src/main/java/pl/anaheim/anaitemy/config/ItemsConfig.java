package pl.anaheim.anaitemy.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.anaheim.anaitemy.AnaItemy;

import java.io.File;
import java.util.List;

public class ItemsConfig {

    private final AnaItemy plugin;
    private FileConfiguration config;
    private File configFile;

    public ItemsConfig(AnaItemy plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        configFile = new File(plugin.getDataFolder(), "items.yml");

        if (!configFile.exists()) {
            plugin.saveResource("items.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reloadConfig() {
        loadConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // ==================== TOTEM ====================

    public String getTotemName() {
        return config.getString("totem-ulaskawienia.name", "&5&lTotem Ułaskawienia");
    }

    public List<String> getTotemLore() {
        return config.getStringList("totem-ulaskawienia.lore");
    }

    public int getTotemCustomModelData() {
        return config.getInt("totem-ulaskawienia.custom-model-data", 1);
    }

    public int getTotemGuiSlot() {
        return config.getInt("totem-ulaskawienia.gui-slot", 0);
    }

    public String getTotemDeathMessage() {
        return config.getString("totem-ulaskawienia.messages.death",
                "&cGracz &7{victim} &czginął z &eTotemem Ułaskawienia&c!");
    }

    public List<String> getTotemBlockedRegions() {
        return config.getStringList("totem-ulaskawienia.blocked-regions");
    }

    // ==================== EXCALIBUR ====================

    public String getExcaliburName() {
        return config.getString("excalibur.name", "&e&lExcalibur");
    }

    public int getExcaliburCustomModelData() {
        return config.getInt("excalibur.custom-model-data", 5);
    }

    public int getExcaliburGuiSlot() {
        return config.getInt("excalibur.gui-slot", 1);
    }

    public int getExcaliburSharpness() {
        return config.getInt("excalibur.enchants.sharpness", 6);
    }

    public int getExcaliburFireAspect() {
        return config.getInt("excalibur.enchants.fire-aspect", 2);
    }

    public int getExcaliburUnbreaking() {
        return config.getInt("excalibur.enchants.unbreaking", 3);
    }

    public double getExcaliburAttackSpeed() {
        return config.getDouble("excalibur.attack-speed", -2.0);
    }

    public double getExcaliburBaseDamage() {
        return config.getDouble("excalibur.base-damage", 11.5);
    }

    public double getExcaliburMaxDamage() {
        return config.getDouble("excalibur.max-damage", 12.0);
    }

    public int getExcaliburMaxKills() {
        return config.getInt("excalibur.max-kills", 100);
    }

    public int getExcaliburBarLength() {
        return config.getInt("excalibur.bar-length", 20);
    }

    public String getExcaliburMessageKillsSet() {
        return config.getString("excalibur.messages.kills-set",
                "&aUstawiono zabójstwa Excalibura na: &f{kills}");
    }

    public String getExcaliburMessageNotHolding() {
        return config.getString("excalibur.messages.not-holding",
                "&cMusisz trzymać Excalibur w ręku!");
    }

    public String getExcaliburMessageInvalidNumber() {
        return config.getString("excalibur.messages.invalid-number",
                "&cPodaj poprawną liczbę!");
    }

    public String getExcaliburMessageNegativeNumber() {
        return config.getString("excalibur.messages.negative-number",
                "&cLiczba zabójstw nie może być ujemna!");
    }

    public List<String> getExcaliburBlockedRegions() {
        return config.getStringList("excalibur.blocked-regions");
    }

    // ==================== HYDRO KLATKA ====================

    public String getHydroKlatkaName() {
        return config.getString("hydro-klatka.name", "&3&lWyrzutnia Hydro Klatki");
    }

    public List<String> getHydroKlatkaLore() {
        return config.getStringList("hydro-klatka.lore");
    }

    public int getHydroKlatkaCustomModelData() {
        return config.getInt("hydro-klatka.custom-model-data", 2);
    }

    public int getHydroKlatkaGuiSlot() {
        return config.getInt("hydro-klatka.gui-slot", 2);
    }

    public long getHydroKlatkaCooldown() {
        return config.getLong("hydro-klatka.cooldown", 180);
    }

    public int getHydroKlatkaRadius() {
        return config.getInt("hydro-klatka.cage.radius", 7);
    }

    public int getHydroKlatkaDuration() {
        return config.getInt("hydro-klatka.cage.duration", 15);
    }

    public int getHydroKlatkaAnimationDuration() {
        return config.getInt("hydro-klatka.cage.animation-duration", 60);
    }

    public String getHydroKlatkaShootSound() {
        return config.getString("hydro-klatka.sounds.shoot.sound", "ITEM_CROSSBOW_SHOOT");
    }

    public float getHydroKlatkaShootVolume() {
        return (float) config.getDouble("hydro-klatka.sounds.shoot.volume", 2.0);
    }

    public float getHydroKlatkaShootPitch() {
        return (float) config.getDouble("hydro-klatka.sounds.shoot.pitch", 1.0);
    }

    public String getHydroKlatkaCustomSound() {
        return config.getString("hydro-klatka.sounds.cage-create.custom-sound", "custom.hydroklatka");
    }

    public float getHydroKlatkaCustomSoundVolume() {
        return (float) config.getDouble("hydro-klatka.sounds.cage-create.volume", 3.0);
    }

    public float getHydroKlatkaCustomSoundPitch() {
        return (float) config.getDouble("hydro-klatka.sounds.cage-create.pitch", 1.0);
    }

    public String getHydroKlatkaExplodeSound() {
        return config.getString("hydro-klatka.sounds.cage-explode.sound", "ENTITY_GENERIC_EXPLODE");
    }

    public float getHydroKlatkaExplodeVolume() {
        return (float) config.getDouble("hydro-klatka.sounds.cage-explode.volume", 1.5);
    }

    public float getHydroKlatkaExplodePitch() {
        return (float) config.getDouble("hydro-klatka.sounds.cage-explode.pitch", 1.0);
    }

    public String getHydroKlatkaRemoveSound() {
        return config.getString("hydro-klatka.sounds.cage-remove.sound", "ENTITY_GENERIC_SPLASH");
    }

    public float getHydroKlatkaRemoveVolume() {
        return (float) config.getDouble("hydro-klatka.sounds.cage-remove.volume", 1.5);
    }

    public float getHydroKlatkaRemovePitch() {
        return (float) config.getDouble("hydro-klatka.sounds.cage-remove.pitch", 1.2);
    }

    public String getHydroKlatkaChunkBlockedSound() {
        return config.getString("hydro-klatka.sounds.chunk-blocked.sound", "BLOCK_GLASS_BREAK");
    }

    public float getHydroKlatkaChunkBlockedVolume() {
        return (float) config.getDouble("hydro-klatka.sounds.chunk-blocked.volume", 1.0);
    }

    public float getHydroKlatkaChunkBlockedPitch() {
        return (float) config.getDouble("hydro-klatka.sounds.chunk-blocked.pitch", 0.5);
    }

    public String getHydroKlatkaBossBarTitle() {
        return config.getString("hydro-klatka.bossbar.title", "&bHydroklatka");
    }

    public List<String> getHydroKlatkaBlockedItems() {
        return config.getStringList("hydro-klatka.blocked-items");
    }

    public List<String> getHydroKlatkaBlockedRegions() {
        return config.getStringList("hydro-klatka.blocked-regions");
    }

    public String getHydroKlatkaMessageCooldown() {
        return config.getString("hydro-klatka.messages.cooldown",
                "&cNie możesz używać tego tak szybko! Pozostało: &f{time}s");
    }

    public String getHydroKlatkaMessageChunkBlocked() {
        return config.getString("hydro-klatka.messages.chunk-blocked",
                "&cNie możesz w tym miejscu stworzyć klatki!");
    }

    public String getHydroKlatkaMessageBlockedRegion() {
        return config.getString("hydro-klatka.messages.blocked-region",
                "&cNie możesz użyć wyrzutni w tym regionie!");
    }

    public String getHydroKlatkaMessageCannotUseInCage() {
        return config.getString("hydro-klatka.messages.cannot-use-in-cage",
                "&cNie możesz tego zrobić w Hydro Klatce!");
    }

    public String getHydroKlatkaActionBarFormat() {
        return config.getString("hydro-klatka.actionbar.cooldown-format",
                "&bHydro Klatka: &f{time}s");
    }

    // ==================== RÓŻDŻKA ILUZJONISTY ====================

    public String getRozdzkailuzjonistyName() {
        return config.getString("rozdzka-iluzjonisty.name", "&5&lRóżdżka Iluzjonisty");
    }

    public List<String> getRozdzkailuzjonistyLore() {
        return config.getStringList("rozdzka-iluzjonisty.lore");
    }

    public int getRozdzkailuzjonistyCustomModelData() {
        return config.getInt("rozdzka-iluzjonisty.custom-model-data", 1);
    }

    public int getRozdzkailuzjonistyGuiSlot() {
        return config.getInt("rozdzka-iluzjonisty.gui-slot", 3);
    }

    public int getRozdzkailuzjonistyUnbreaking() {
        return config.getInt("rozdzka-iluzjonisty.enchants.unbreaking", 10);
    }

    // FANGS (LPM)
    public long getRozdzkailuzjonistyFangsCooldown() {
        return config.getLong("rozdzka-iluzjonisty.fangs.cooldown", 20);
    }

    public int getRozdzkailuzjonistyFangsLength() {
        return config.getInt("rozdzka-iluzjonisty.fangs.length", 7);
    }

    public int getRozdzkailuzjonistyFangsWidth() {
        return config.getInt("rozdzka-iluzjonisty.fangs.width", 3);
    }

    public double getRozdzkailuzjonistyFangsSpacing() {
        return config.getDouble("rozdzka-iluzjonisty.fangs.spacing", 1.0);
    }

    public double getRozdzkailuzjonistyFangsDamage() {
        return config.getDouble("rozdzka-iluzjonisty.fangs.damage", 12.0);
    }

    public double getRozdzkailuzjonistyFangsSpeed() {
        return config.getDouble("rozdzka-iluzjonisty.fangs.speed", 0.5);
    }

    public String getRozdzkailuzjonistyFangsMessageActivated() {
        return config.getString("rozdzka-iluzjonisty.fangs.messages.activated",
                "&aSzczęki Evokera &7zostały &aaktywowane&7!");
    }

    public String getRozdzkailuzjonistyFangsMessageCooldownTitle() {
        return config.getString("rozdzka-iluzjonisty.fangs.messages.cooldown-title",
                "&cUmiejętność w odnowieniu");
    }

    public String getRozdzkailuzjonistyFangsMessageCooldownSubtitle() {
        return config.getString("rozdzka-iluzjonisty.fangs.messages.cooldown-subtitle",
                "&7Do użycia za: &e{seconds}s");
    }

    // VANISH (PPM)
    public long getRozdzkailuzjonistyVanishCooldown() {
        return config.getLong("rozdzka-iluzjonisty.vanish.cooldown", 60);
    }

    public int getRozdzkailuzjonistyVanishDuration() {
        return config.getInt("rozdzka-iluzjonisty.vanish.duration", 4);
    }

    public double getRozdzkailuzjonistyVanishNpcSpeed() {
        return config.getDouble("rozdzka-iluzjonisty.vanish.npc-speed", 1.0);
    }

    public String getRozdzkailuzjonistyVanishSoundActivate() {
        return config.getString("rozdzka-iluzjonisty.vanish.sounds.activate", "ENTITY_ENDERMAN_AMBIENT");
    }

    public String getRozdzkailuzjonistyVanishSoundDeactivate() {
        return config.getString("rozdzka-iluzjonisty.vanish.sounds.deactivate", "ENTITY_ENDERMAN_TELEPORT");
    }

    public String getRozdzkailuzjonistyVanishMessageActivated() {
        return config.getString("rozdzka-iluzjonisty.vanish.messages.activated",
                "&aZniknięcie &7zostało &aaktywowane&7!");
    }

    public String getRozdzkailuzjonistyVanishMessageCooldownTitle() {
        return config.getString("rozdzka-iluzjonisty.vanish.messages.cooldown-title",
                "&cUmiejętność w odnowieniu");
    }

    public String getRozdzkailuzjonistyVanishMessageCooldownSubtitle() {
        return config.getString("rozdzka-iluzjonisty.vanish.messages.cooldown-subtitle",
                "&7Do użycia za: &e{seconds}s");
    }

    public List<String> getRozdzkailuzjonistyBlockedRegions() {
        return config.getStringList("rozdzka-iluzjonisty.blocked-regions");
    }
        // ==================== WĘDKA NIELOTA ====================

    public String getWedkaNielotaName() {
        return config.getString("wedka-nielota.name", "&5&lWędka nielota");
    }

    public List<String> getWedkaNielotaLore() {
        return config.getStringList("wedka-nielota.lore");
    }

    public int getWedkaNielotaCustomModelData() {
        return config.getInt("wedka-nielota.custom-model-data", 2);
    }

    public int getWedkaNielotaGuiSlot() {
        return config.getInt("wedka-nielota.gui-slot", 4);
    }

    public int getWedkaNielotaUnbreaking() {
        return config.getInt("wedka-nielota.enchants.unbreaking", 10);
    }

    public int getWedkaNielotaCurseDuration() {
        return config.getInt("wedka-nielota.curse-duration", 12);
    }

    public long getWedkaNielotaCooldown() {
        return config.getLong("wedka-nielota.cooldown", 30);
    }

    public double getWedkaNielotaBugowanieFallSpeed() {
        return config.getDouble("wedka-nielota.bugowanie-fall-speed", 2.0);
    }

    public int getWedkaNielotaBugowanieResetDuration() {
        return config.getInt("wedka-nielota.bugowanie-reset-duration", 4);
    }

    public List<String> getWedkaNielotaBlockedRegions() {
        return config.getStringList("wedka-nielota.blocked-regions");
    }

    // Messages
    public String getWedkaNielotaCaughtTitle() {
        return config.getString("wedka-nielota.messages.caught-title", "&c&lZłapany!");
    }

    public String getWedkaNielotaCaughtSubtitle() {
        return config.getString("wedka-nielota.messages.caught-subtitle", "&7Złapany przez: &e{attacker}");
    }

    public String getWedkaNielotaCatcherTitle() {
        return config.getString("wedka-nielota.messages.catcher-title", "&a&lZłapałeś gracza!");
    }

    public String getWedkaNielotaCatcherSubtitle() {
        return config.getString("wedka-nielota.messages.catcher-subtitle", "&7Złapany gracz: &e{victim}");
    }

    public String getWedkaNielotaReleasedTitle() {
        return config.getString("wedka-nielota.messages.released-title", "&c&lPuścił cię!");
    }

    public String getWedkaNielotaReleasedSubtitle() {
        return config.getString("wedka-nielota.messages.released-subtitle", "&cPuścił cię gracz!");
    }

    public String getWedkaNielotaReleaserTitle() {
        return config.getString("wedka-nielota.messages.releaser-title", "&c&lPuściłeś gracza!");
    }

    public String getWedkaNielotaReleaserSubtitle() {
        return config.getString("wedka-nielota.messages.releaser-subtitle", "&cPuściłeś gracza!");
    }

    public String getWedkaNielotaFreedTitle() {
        return config.getString("wedka-nielota.messages.freed-title", "&c&lUwolniony!");
    }

    public String getWedkaNielotaFreedSubtitle() {
        return config.getString("wedka-nielota.messages.freed-subtitle", "&7Klątwa została zdjęta!");
    }

    public String getWedkaNielotaCooldownMessage() {
        return config.getString("wedka-nielota.messages.cooldown-message", "&cNie możesz użyć wędki tak szybko!");
    }

    // BossBar
    public String getWedkaNielotaBossBarTitle() {
        return config.getString("wedka-nielota.bossbar.title", "&cPosiadasz klątwę! Nie możesz latać przez &e{seconds}s");
    }

    public String getWedkaNielotaBossBarTitleWaiting() {
        return config.getString("wedka-nielota.bossbar.title-waiting", "&cPosiadasz klątwę! Nie możesz latać przez &e<1s");
    }

    public String getWedkaNielotaBossBarColor() {
        return config.getString("wedka-nielota.bossbar.color", "RED");
    }
}
