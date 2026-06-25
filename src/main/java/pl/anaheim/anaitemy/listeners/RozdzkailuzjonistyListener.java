    @EventHandler(priority = EventPriority.HIGH)
    public void onFangDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof EvokerFangs fang)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        RozdzkailuzjonistyManager manager = plugin.getRozdzkailuzjonistyManager();

        if (manager.hasFangDamaged(fang, victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!manager.canFangDamageInRegion(victim.getLocation())) {
            event.setCancelled(true);
            fang.remove();
            manager.cleanupFang(fang);
            return;
        }

        // ✅ POBIERZ WŁAŚCICIELA SZCZĘK
        UUID ownerUUID = manager.getFangOwner(fang);

        // ✅ Jeśli ofiara to właściciel szczęk - nie zadawaj damage
        if (ownerUUID != null && ownerUUID.equals(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        Player attacker = ownerUUID != null ? plugin.getServer().getPlayer(ownerUUID) : null;

        if (attacker != null) {
            // ✅ SPRAWDŹ OCHRONĘ PRZED RÓŻDŻKĄ
            if (plugin.getItemProtectionManager().isProtected(victim, "rozdzka-iluzjonisty")) {
                event.setCancelled(true);
                
                int secondsLeft = plugin.getItemProtectionManager()
                        .getRemainingSeconds(victim, "rozdzka-iluzjonisty");
                
                plugin.getItemProtectionManager()
                        .notifyAttacker(attacker, "rozdzka-iluzjonisty", secondsLeft);
                
                manager.markFangDamaged(fang, victim.getUniqueId());
                fang.remove();
                manager.cleanupFang(fang);
                return;
            }
        }

        // ✅ ANULUJ domyślne damage
        event.setCancelled(true);

        ItemsConfig config = plugin.getItemsConfig();
        double damage = config.getRozdzkailuzjonistyFangsDamage();

        double newHealth = victim.getHealth() - damage;

        if (newHealth <= 0) {
            victim.setHealth(0.0);
        } else {
            victim.setHealth(newHealth);
        }

        // ✅ NAŁÓŻ OCHRONĘ PO ZADANIU DAMAGE
        plugin.getItemProtectionManager().applyProtection(victim, "rozdzka-iluzjonisty");

        manager.markFangDamaged(fang, victim.getUniqueId());
        fang.remove();
        manager.cleanupFang(fang);
    }
