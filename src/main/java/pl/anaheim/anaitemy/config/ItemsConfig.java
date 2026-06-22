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
