    private void startVanish(Player player) {
        ItemsConfig config = plugin.getItemsConfig();
        int duration = config.getRozdzkailuzjonistyVanishDuration();
        double npcSpeed = config.getRozdzkailuzjonistyVanishNpcSpeed();
        
        // Stwórz NPC - POPRAWIONE
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(
                org.bukkit.entity.EntityType.PLAYER,  // POPRAWIONE - używamy EntityType zamiast NPCType
                player.getName()
        );
        
        // Ustaw skin gracza
        npc.getOrAddTrait(SkinTrait.class).setSkinName(player.getName());
        
        // Spawn NPC w lokalizacji gracza
        Location npcLoc = player.getLocation().clone();
        npc.spawn(npcLoc);
        
        // Skopiuj ekwipunek
        if (npc.getEntity() instanceof Player npcPlayer) {
            npcPlayer.getInventory().setArmorContents(player.getInventory().getArmorContents());
            npcPlayer.getInventory().setItemInMainHand(player.getInventory().getItemInMainHand());
            npcPlayer.getInventory().setItemInOffHand(player.getInventory().getItemInOffHand());
            
            // Ustaw max HP
            npcPlayer.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)
                    .setBaseValue(player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            npcPlayer.setHealth(player.getHealth());
        }
        
        // Ustaw nieśmiertelność NPC (inni nie mogą go uderzyć)
        npc.setProtected(true);
        
        // Ukryj gracza (invisible)
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hidePlayer(plugin, player);
            }
        }
        
        // Zapisz dane zniknięcia
        VanishData data = new VanishData(player, npc, System.currentTimeMillis() + (duration * 1000L));
        activeVanishes.put(player.getUniqueId(), data);
        
        // Ruch NPC do przodu
        Vector direction = player.getLocation().getDirection().normalize();
        direction.setY(0);
        direction.normalize().multiply(npcSpeed);
        
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = duration * 20;
            
            @Override
            public void run() {
                if (!activeVanishes.containsKey(player.getUniqueId())) {
                    cancel();
                    return;
                }
                
                if (ticks >= maxTicks) {
                    endVanish(player, false);
                    cancel();
                    return;
                }
                
                // Ruch NPC
                if (npc.isSpawned() && npc.getEntity() != null) {
                    Location current = npc.getEntity().getLocation();
                    Location next = current.clone().add(direction);
                    
                    // Sprawdź czy przed NPC jest ściana
                    if (next.getBlock().getType().isSolid()) {
                        // Zatrzymaj się przed ścianą
                        return;
                    }
                    
                    npc.getEntity().teleport(next);
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
