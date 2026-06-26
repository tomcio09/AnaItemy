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

    public String getExcaliburMessageTooFast() {
        return config.getString("excalibur.messages.too-fast",
                "&cNie możesz zliczyć zabójstwa &f{victim} &c- poczekaj &f{time}s&c!");
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

    public String getWedkaNielotaBossBarTitle() {
        return config.getString("wedka-nielota.bossbar.title", "&cPosiadasz klątwę! Nie możesz latać przez &e{seconds}s");
    }

    public String getWedkaNielotaBossBarTitleWaiting() {
        return config.getString("wedka-nielota.bossbar.title-waiting", "&cPosiadasz klątwę! Nie możesz latać przez &e<1s");
    }

    public String getWedkaNielotaBossBarColor() {
        return config.getString("wedka-nielota.bossbar.color", "RED");
    }

    // ==================== HYDRO KLATKA SOUNDS (DODATKOWE) ====================

    public String getHydroKlatkaSplashSound() {
        return config.getString("hydro-klatka.sounds.splash.sound", "ENTITY_GENERIC_SPLASH");
    }

    public float getHydroKlatkaSplashVolume() {
        return (float) config.getDouble("hydro-klatka.sounds.splash.volume", 1.5);
    }

    public float getHydroKlatkaSplashPitch() {
        return (float) config.getDouble("hydro-klatka.sounds.splash.pitch", 1.0);
    }

    public String getHydroKlatkaAmbientSound() {
        return config.getString("hydro-klatka.sounds.ambient.sound", "BLOCK_WATER_AMBIENT");
    }

    public float getHydroKlatkaAmbientVolume() {
        return (float) config.getDouble("hydro-klatka.sounds.ambient.volume", 3.0);
    }

    public float getHydroKlatkaAmbientPitch() {
        return (float) config.getDouble("hydro-klatka.sounds.ambient.pitch", 1.0);
    }

    // ==================== SAKIEWKA DROPU ====================

    public String getSakiewkaDropuName() {
        return config.getString("sakiewka-dropu.name", "&a&lSakiewka dropu");
    }

    public List<String> getSakiewkaDropuLore() {
        return config.getStringList("sakiewka-dropu.lore");
    }

    public int getSakiewkaDropuCustomModelData() {
        return config.getInt("sakiewka-dropu.custom-model-data", 1);
    }

    public int getSakiewkaDropuGuiSlot() {
        return config.getInt("sakiewka-dropu.gui-slot", 5);
    }

    public List<String> getSakiewkaBlockedRegions() {
        return config.getStringList("sakiewka-dropu.blocked-regions");
    }

    public List<String> getSakiewkaBlockedRegionsNoPayout() {
        return config.getStringList("sakiewka-dropu.blocked-regions-no-payout");
    }

    // ==================== COMBAT INTEGRATION ====================

    public boolean isCombatIntegrationEnabled() {
        return config.getBoolean("combat-integration.enabled", true);
    }

    public boolean isBlockSakiewkaInCombat() {
        return config.getBoolean("combat-integration.block-sakiewka-in-combat", true);
    }

    public boolean isHydroKlatkaTagPlayers() {
        return config.getBoolean("combat-integration.hydroklatka-tag-players", true);
    }

    public boolean isActionBarIntegrationEnabled() {
        return config.getBoolean("combat-integration.actionbar.enabled", true);
    }

    public int getActionBarResumeDelay() {
        return config.getInt("combat-integration.actionbar.resume-delay", 40);
    }

    public String getSakiewkaCombatBlockedMessage() {
        return config.getString("sakiewka-dropu.messages.combat-blocked",
                "&cNie możesz otworzyć sakiewki podczas walki!");
    }

    // ==================== ITEM PROTECTION ====================

    public boolean isItemProtectionEnabled() {
        return plugin.getConfig().getBoolean("item-protection.enabled", true);
    }

    public int getItemProtectionDuration() {
        return plugin.getConfig().getInt("item-protection.duration", 4);
    }

    public boolean doesItemRespectProtection(String itemId) {
        return config.getBoolean(itemId + ".respects-protection", false);
    }

    public boolean shouldNotifyAttacker(String itemId) {
        return config.getBoolean(itemId + ".notify-attacker", false);
    }

    public String getProtectionTitle(String itemId) {
        return config.getString(itemId + ".protection-messages.title", "&c&lNie zadziałało!");
    }

    public String getProtectionSubtitle(String itemId) {
        return config.getString(itemId + ".protection-messages.subtitle",
                "&7Spróbuj ponownie za: &e{seconds_left}s&7!");
    }

    // ==================== BLOK WIDMO ====================

    public String getBlokWidmoName() {
        return config.getString("blok-widmo.name", "&c&lBlok widmo");
    }

    public List<String> getBlokWidmoLore() {
        return config.getStringList("blok-widmo.lore");
    }

    public int getBlokWidmoCustomModelData() {
        return config.getInt("blok-widmo.custom-model-data", 0);
    }

    public int getBlokWidmoGuiSlot() {
        return config.getInt("blok-widmo.gui-slot", 7);
    }

    public int getBlokWidmoRadius() {
        return config.getInt("blok-widmo.radius", 20);
    }

    public int getBlokWidmoEffectDuration() {
        return config.getInt("blok-widmo.effect-duration", 160);
    }

    public double getBlokWidmoHealthReduction() {
        return config.getDouble("blok-widmo.health-reduction", 20);
    }

    public double getBlokWidmoMinimumHealth() {
        return config.getDouble("blok-widmo.minimum-health", 20);
    }

    public long getBlokWidmoCooldown() {
        return config.getLong("blok-widmo.cooldown", 180);
    }

    public List<String> getBlokWidmoBlockedRegions() {
        return config.getStringList("blok-widmo.blocked-regions");
    }

    public String getBlokWidmoActivateSound() {
        return config.getString("blok-widmo.sounds.activate", "BLOCK_BEACON_ACTIVATE");
    }

    public String getBlokWidmoDeactivateSound() {
        return config.getString("blok-widmo.sounds.deactivate", "BLOCK_BEACON_DEACTIVATE");
    }

    public String getBlokWidmoBossBarTitle() {
        return config.getString("blok-widmo.bossbar.title",
                "&cBlok widmo: &fPosiadasz obniżony limit serc przez {time_left}");
    }

    public String getBlokWidmoBossBarColor() {
        return config.getString("blok-widmo.bossbar.color", "PURPLE");
    }

    public String getBlokWidmoPlacedSubtitle() {
        return config.getString("blok-widmo.messages.placed-subtitle", "&cPostawiono blok widmo!");
    }

    public String getBlokWidmoAffectedTitle() {
        return config.getString("blok-widmo.messages.affected-title", "&c&lBlok widmo");
    }

    public String getBlokWidmoAffectedSubtitle() {
        return config.getString("blok-widmo.messages.affected-subtitle",
                "&7coś się dzieje z twoim zdrowiem...");
    }

    public String getBlokWidmoCooldownMessage() {
        return config.getString("blok-widmo.messages.cooldown",
                "&cMusisz poczekać jeszcze {time_left} zanim będziesz mógł użyć tego przedmiotu ponownie!");
    }

    // ==================== SIEKIERA GRINCHA ====================

    public String getSiekieraGrinchaName() {
        return config.getString("siekiera-grincha.name", "&2&lSiekiera Grincha");
    }

    public List<String> getSiekieraGrinchaLore() {
        return config.getStringList("siekiera-grincha.lore");
    }

    public int getSiekieraGrinchaCustomModelData() {
        return config.getInt("siekiera-grincha.custom-model-data", 1);
    }

    public int getSiekieraGrinchaGuiSlot() {
        return config.getInt("siekiera-grincha.gui-slot", 8);
    }

    public double getSiekieraGrinchaDamagePercent() {
        return config.getDouble("siekiera-grincha.damage-percent", 30.0);
    }

    public long getSiekieraGrinchaCooldown() {
        return config.getLong("siekiera-grincha.cooldown", 60);
    }

    public List<String> getSiekieraGrinchaBlockedRegions() {
        return config.getStringList("siekiera-grincha.blocked-regions");
    }

    public String getSiekieraGrinchaAttackerSubtitle() {
        return config.getString("siekiera-grincha.messages.attacker-subtitle",
                "&7Uderzyłeś &f{nick_victim} &7siekierą &agrincha&7!");
    }

    public String getSiekieraGrinchaVictimSubtitle() {
        return config.getString("siekiera-grincha.messages.victim-subtitle",
                "&7Zostałeś uderzony &asiekierą grincha&7!");
    }

    public String getSiekieraGrinchaCooldownSubtitle() {
        return config.getString("siekiera-grincha.messages.cooldown-subtitle",
                "&2Siekierę Grincha &7możesz użyć dopiero za &a{seconds_left}&7!");
    }

    // ==================== HYDRO TRÓJZĄB ====================

    public String getHydroTrojzabName() {
        return config.getString("hydro-trojzab.name", "&3&lHydro Trójząb");
    }

    public List<String> getHydroTrojzabLore() {
        return config.getStringList("hydro-trojzab.lore");
    }

    public int getHydroTrojzabCustomModelData() {
        return config.getInt("hydro-trojzab.custom-model-data", 1);
    }

    public int getHydroTrojzabGuiSlot() {
        return config.getInt("hydro-trojzab.gui-slot", 9);
    }

    public long getHydroTrojzabShotCooldown() {
        return config.getLong("hydro-trojzab.shot.cooldown", 60);
    }

    public long getHydroTrojzabLaunchCooldown() {
        return config.getLong("hydro-trojzab.launch.cooldown", 15);
    }

    public double getHydroTrojzabImpactDamage() {
        return config.getDouble("hydro-trojzab.shot.damage", 10.0);
    }

    public double getHydroTrojzabImpactRadius() {
        return config.getDouble("hydro-trojzab.shot.radius", 4.0);
    }

    public double getHydroTrojzabKnockbackHorizontal() {
        return config.getDouble("hydro-trojzab.shot.knockback-horizontal", 1.2);
    }

    public double getHydroTrojzabKnockbackUpward() {
        return config.getDouble("hydro-trojzab.shot.knockback-upward", 0.35);
    }

    public double getHydroTrojzabLaunchPower() {
        return config.getDouble("hydro-trojzab.launch.power", 3.2);
    }

    public List<String> getHydroTrojzabBlockedRegions() {
        return config.getStringList("hydro-trojzab.blocked-regions");
    }

    public String getHydroTrojzabShotCooldownSubtitle() {
        return config.getString("hydro-trojzab.messages.shot-cooldown-subtitle",
                "&7Cios pirunem możesz użyć za: &b{seconds_left}s&7!");
    }

    public String getHydroTrojzabLaunchCooldownSubtitle() {
        return config.getString("hydro-trojzab.messages.launch-cooldown-subtitle",
                "&7Wystrzelenia możesz użyć za: &b{seconds_left}&7!");
    }
        // ==================== CUDOWNA LATARNIA ====================

    public String getCudownaLatarniaName() {
        return config.getString("cudowna-latarnia.name", "&d&lCudowna Latarnia");
    }

    public int getCudownaLatarniaGuiSlot() {
        return config.getInt("cudowna-latarnia.gui-slot", 10);
    }

    public int getCudownaLatarniaDuration() {
        return config.getInt("cudowna-latarnia.duration", 30);
    }

    public long getCudownaLatarniaCooldown() {
        return config.getLong("cudowna-latarnia.cooldown", 180);
    }

    public long getCudownaLatarniaChunkCooldown() {
        return config.getLong("cudowna-latarnia.chunk-cooldown", 60);
    }

    public int getCudownaLatarniaRegenDuration() {
        return config.getInt("cudowna-latarnia.effects.regeneration.duration", 20);
    }

    public int getCudownaLatarniaRegenLevel() {
        return config.getInt("cudowna-latarnia.effects.regeneration.level", 5);
    }

    public int getCudownaLatarniaAbsorptionDuration() {
        return config.getInt("cudowna-latarnia.effects.absorption.duration", 10);
    }

    public int getCudownaLatarniaAbsorptionLevel() {
        return config.getInt("cudowna-latarnia.effects.absorption.level", 6);
    }

    public int getCudownaLatarniaStrengthDuration() {
        return config.getInt("cudowna-latarnia.effects.strength.duration", 10);
    }

    public int getCudownaLatarniaStrengthLevel() {
        return config.getInt("cudowna-latarnia.effects.strength.level", 2);
    }

    public List<String> getCudownaLatarniaBlockedRegions() {
        return config.getStringList("cudowna-latarnia.blocked-regions");
    }

    public String getCudownaLatarniaActivatedSubtitle() {
        return config.getString("cudowna-latarnia.messages.activated-subtitle",
                "&5Cudowna Latarnia! &7Aktywowana na &d30 sekund&7!");
    }

    public String getCudownaLatarniaDestroyedSubtitle() {
        return config.getString("cudowna-latarnia.messages.destroyed-subtitle",
                "&7Zniszczyłeś &5legendarną fontannę&7!");
    }

    public String getCudownaLatarniaBossBarTitle() {
        return config.getString("cudowna-latarnia.bossbar.title",
                "&5Cudowna Latarnia aktywna! &7(&d{seconds_left}s&7)");
    }

    public String getCudownaLatarniaBossBarColor() {
        return config.getString("cudowna-latarnia.bossbar.color", "PURPLE");
    }
        // ==================== RÓG JEDNOROŻCA ====================

    public int getRogJednorozcaGuiSlot() {
        return config.getInt("rog-jednorozca.gui-slot", 11);
    }

    public long getRogJednorozcaCooldown() {
        return config.getLong("rog-jednorozca.cooldown", 60);
    }

    public int getRogJednorozcaDuration() {
        return config.getInt("rog-jednorozca.duration", 8);
    }

    public int getRogJednorozcaMaxBlocks() {
        return config.getInt("rog-jednorozca.max-blocks", 100);
    }

    public int getRogJednorozcaStunDuration() {
        return config.getInt("rog-jednorozca.stun-duration", 3);
    }

    public List<String> getRogJednorozcaBlockedRegions() {
        return config.getStringList("rog-jednorozca.blocked-regions");
    }

    public String getRogJednorozcaCooldownSubtitle() {
        return config.getString("rog-jednorozca.messages.cooldown-subtitle",
                "&7Możesz użyć dopiero za: &d{seconds_left}s&7!");
    }

    public String getRogJednorozcaStunSubtitle() {
        return config.getString("rog-jednorozca.messages.stun-subtitle",
                "&cZostałeś ogłuszony!");
    }
        // ==================== BOSKI TOPÓR ====================

    public int getBoskiToporGuiSlot() {
        return config.getInt("boski-topor.gui-slot", 12);
    }

    public long getBoskiToporCooldown() {
        return config.getLong("boski-topor.cooldown", 60);
    }

    public int getBoskiToporInvincibilityDuration() {
        return config.getInt("boski-topor.invincibility-duration", 3);
    }

    public int getBoskiToporGlowDuration() {
        return config.getInt("boski-topor.glow-duration", 2);
    }

    public double getBoskiToporKnockbackRadius() {
        return config.getDouble("boski-topor.knockback-radius", 5.0);
    }

    public double getBoskiToporKnockbackPower() {
        return config.getDouble("boski-topor.knockback-power", 1.8);
    }

    public List<String> getBoskiToporBlockedRegions() {
        return config.getStringList("boski-topor.blocked-regions");
    }

    public String getBoskiToporActivatedSubtitle() {
        return config.getString("boski-topor.messages.activated-subtitle",
                "&bAktywowałeś boski topór&7!");
    }

    public String getBoskiToporCooldownSubtitle() {
        return config.getString("boski-topor.messages.cooldown-subtitle",
                "&bBoski topór &7możesz użyć dopiero za &f{seconds_left}");
    }
        // ==================== SUPER MARCHEWKA ====================

    public int getSuperMarchewkaGuiSlot() {
        return config.getInt("super-marchewka.gui-slot", 13);
    }

    public long getSuperMarchewkaCooldown() {
        return config.getLong("super-marchewka.cooldown", 60);
    }

    public int getSuperMarchewkaEffectDuration() {
        return config.getInt("super-marchewka.effect-duration", 10);
    }

    public List<String> getSuperMarchewkaBlockedRegions() {
        return config.getStringList("super-marchewka.blocked-regions");
    }

    public String getSuperMarchewkaCooldownSubtitle() {
        return config.getString("super-marchewka.messages.cooldown-subtitle",
                "&6Marchewkę &7możesz użyć za: &6{seconds_left}");
    }

    public String getSuperMarchewkaSuperTitle() {
        return config.getString("super-marchewka.messages.super-title", "&6&lSuper Marchewka");
    }

    public String getSuperMarchewkaSuperSubtitle() {
        return config.getString("super-marchewka.messages.super-subtitle",
                "&7Zwiększony x2 na &610 sekund&7!");
    }

    public String getSuperMarchewkaMiniTitle() {
        return config.getString("super-marchewka.messages.mini-title", "&b&lMini Marchewka");
    }

    public String getSuperMarchewkaMiniSubtitle() {
        return config.getString("super-marchewka.messages.mini-subtitle",
                "&aZmniejszony o 50% na 10 sekund!");
    }
        // ==================== ŁOPATA GRINCHA ====================

    public long getLopataGrinchaCooldown() {
        return config.getLong("lopata-grincha.cooldown", 30);
    }

    public List<String> getLopataGrinchaBlockedRegions() {
        return config.getStringList("lopata-grincha.blocked-regions");
    }

    public String getLopataGrinchaAttackerSubtitle() {
        return config.getString("lopata-grincha.messages.attacker-subtitle",
                "&7Uderzyłeś gracza &f{nick} &7łopatą &agrincha&7!");
    }

    public String getLopataGrinchaVictimSubtitle() {
        return config.getString("lopata-grincha.messages.victim-subtitle",
                "&7Zostałeś uderzony &ałopatą grincha&7!");
    }

    public String getLopataGrinchaCooldownSubtitle() {
        return config.getString("lopata-grincha.messages.cooldown-subtitle",
                "&aŁopatę grincha &7możesz użyć za: &a{seconds_left}&7!");
    }
    // ==================== ARCUS MAGNUS ====================

    public int getArcusMagnusGuiSlot() {
        return config.getInt("arcus-magnus.gui-slot", 16);
    }

    public List<String> getArcusMagnusBlockedRegions() {
        return config.getStringList("arcus-magnus.blocked-regions");
    }
        // ==================== KRÓLICZY MIECZ ====================

    public int getKroliczyMieczGuiSlot() {
        return config.getInt("kroliczy-miecz.gui-slot", 17);
    }

    public long getKroliczyMieczCooldown() {
        return config.getLong("kroliczy-miecz.cooldown", 60);
    }

    public int getKroliczyMieczCurseDuration() {
        return config.getInt("kroliczy-miecz.curse-duration", 4);
    }

    public List<String> getKroliczyMieczBlockedRegions() {
        return config.getStringList("kroliczy-miecz.blocked-regions");
    }

    public String getKroliczyMieczAttackerSubtitle() {
        return config.getString("kroliczy-miecz.messages.attacker-subtitle",
                "&7Zablokowałeś &3skakanie &7graczowi &f{nick_victim}&7!");
    }

    public String getKroliczyMieczVictimTitle() {
        return config.getString("kroliczy-miecz.messages.victim-title",
                "&3Królicza klątwa");
    }

    public String getKroliczyMieczVictimSubtitle() {
        return config.getString("kroliczy-miecz.messages.victim-subtitle",
                "&7Nie możesz skakać przez 4 sekundy!");
    }
        // ==================== PIEKIELNY MIECZ ====================

    public int getPiekielnyMieczFireDuration() {
        return config.getInt("piekielny-miecz.fire-duration", 20);
    }

    // ==================== SMOCZY MIECZ ====================

    public long getSmoczyMieczCooldown() {
        return config.getLong("smoczy-miecz.cooldown", 60);
    }

    public List<String> getSmoczyMieczBlockedRegions() {
        return config.getStringList("smoczy-miecz.blocked-regions");
    }

    public String getSmoczyMieczCooldownSubtitle() {
        return config.getString("smoczy-miecz.messages.cooldown-subtitle",
                "&dSmoczy miecz &7możesz użyć za: &d{seconds_left}");
    }
        // ==================== KOSA ====================

    public long getKosaCooldown() {
        return config.getLong("kosa.cooldown", 60);
    }

    // ==================== ŁUK KUPIDYNA ====================

    public long getLukKupidynaCooldown() {
        return config.getLong("luk-kupidyna.cooldown", 60);
    }

    public double getLukKupidynaBlindChance() {
        return config.getDouble("luk-kupidyna.blind-chance", 25.0);
    }

    // ==================== OŚLEPIENIE (WSPÓLNE) ====================

    public int getOslepienieDuration() {
        return config.getInt("oslepienie.duration", 4);
    }

    public List<String> getOslepienieBlockedRegions() {
        return config.getStringList("oslepienie.blocked-regions");
    }

    public String getOslepienieVictimSubtitle() {
        return config.getString("oslepienie.messages.victim-subtitle",
                "&7Zostałeś oślepiony!");
    }

    public String getOslepienieAttackerSubtitle() {
        return config.getString("oslepienie.messages.attacker-subtitle",
                "&7Oślepiłeś gracza &f{nick_victim}&7!");
    }

    public String getOslepienieCooldownSubtitle() {
        return config.getString("oslepienie.messages.cooldown-subtitle",
                "&7Oślepienia możesz użyć za: &f{seconds_left}&7!");
    }
        // ==================== MARCHEWKOWY MIECZ ====================

    public long getMarchewkowyMieczCooldown() { return config.getLong("marchewkowy-miecz.cooldown", 60); }
    public int getMarchewkowyMieczFreezeDuration() { return config.getInt("marchewkowy-miecz.freeze-duration", 1); }
    public List<String> getMarchewkowyMieczBlockedRegions() { return config.getStringList("marchewkowy-miecz.blocked-regions"); }
    public String getMarchewkowyMieczAttackerSubtitle() { return config.getString("marchewkowy-miecz.messages.attacker-subtitle", "&bZamroziłeś &7gracza: &f{nick_victim}&7!"); }
    public String getMarchewkowyMieczVictimSubtitle() { return config.getString("marchewkowy-miecz.messages.victim-subtitle", "&7Zostałeś &bzamrożony&7!"); }
    public String getMarchewkowyMieczCooldownSubtitle() { return config.getString("marchewkowy-miecz.messages.cooldown-subtitle", "&bZamrożenia &7możesz użyć za: &f{seconds_left}&7!"); }

    // ==================== MARCHEWKOWA KUSZA ====================

    public long getMarchewkowaKuszaCooldown() { return config.getLong("marchewkowa-kusza.cooldown", 60); }
    public List<String> getMarchewkowaKuszaBlockedRegions() { return config.getStringList("marchewkowa-kusza.blocked-regions"); }
    public String getMarchewkowaKuszaVictimSubtitle() { return config.getString("marchewkowa-kusza.messages.victim-subtitle", "&7Zostałeś przyciągnięty przez gracza &f{attacker}&7!"); }
    public String getMarchewkowaKuszaAttackerSubtitle() { return config.getString("marchewkowa-kusza.messages.attacker-subtitle", "&7Przyciągnąłeś gracza &f{victim_name}&7!"); }
        // ==================== WĘDKA SURFERKA ====================

    public long getWedkaSurferkaCooldown() { return config.getLong("wedka-surferka.cooldown", 15); }
    public double getWedkaSurferkaPower() { return config.getDouble("wedka-surferka.power", 2.0); }
    public String getWedkaSurferkaCooldownSubtitle() {
        return config.getString("wedka-surferka.messages.cooldown-subtitle",
                "&bSurferkę &7możesz użyć za: &f{seconds_left}&7!");
    }

    // ==================== ZATRUTY OŁÓWEK ====================

    public long getZatrutyOlowekCooldown() { return config.getLong("zatruty-olowek.cooldown", 60); }
    public int getZatrutyOlowekWeaknessDuration() { return config.getInt("zatruty-olowek.weakness-duration", 10); }
    public int getZatrutyOlowekPoisonDuration() { return config.getInt("zatruty-olowek.poison-duration", 10); }
    public List<String> getZatrutyOlowekBlockedRegions() { return config.getStringList("zatruty-olowek.blocked-regions"); }
    public String getZatrutyOlowekAttackerSubtitle() {
        return config.getString("zatruty-olowek.messages.attacker-subtitle", "&7Zatrułeś gracza &f{victim}&7!");
    }
    public String getZatrutyOlowekVictimSubtitle() {
        return config.getString("zatruty-olowek.messages.victim-subtitle", "&7Zostałeś otruty!");
    }
    public String getZatrutyOlowekCooldownSubtitle() {
        return config.getString("zatruty-olowek.messages.cooldown-subtitle", "&aOtrucie &7możesz użyć za: &f{seconds_left}&7!");
    }

    // ==================== PIEKIELNA TARCZA ====================

    public String getPiekielnaTarczaAttackerSubtitle() {
        return config.getString("piekielna-tarcza.messages.attacker-subtitle",
                "&7Gracz &f{shield_handler} &7odbił twój cios!");
    }
    public String getPiekielnaTarczaDefenderSubtitle() {
        return config.getString("piekielna-tarcza.messages.defender-subtitle",
                "&7Odbiłeś cios gracza &f{attacker_bez_tarczy}&7!");
    }

    // ==================== KUKURYDZA ====================

    public long getKukurydzaCooldown() { return config.getLong("kukurydza.cooldown", 60); }
    public double getKukurydzaRadius() { return config.getDouble("kukurydza.radius", 5.0); }
    public int getKukurydzaDurabilityDamage() { return config.getInt("kukurydza.durability-damage", 30); }
    public List<String> getKukurydzaBlockedRegions() { return config.getStringList("kukurydza.blocked-regions"); }
    public String getKukurydzaAttackerSubtitle() {
        return config.getString("kukurydza.messages.attacker-subtitle", "&7Wystrzeliłeś &akukurydzą&7!");
    }
    public String getKukurydzaCooldownSubtitle() {
        return config.getString("kukurydza.messages.cooldown-subtitle", "&7Użyć &akukurydzy &7możesz za: &f{seconds_left}&7!");
    }
}
