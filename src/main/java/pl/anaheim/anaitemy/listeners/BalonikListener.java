package pl.anaheim.anaitemy.listeners;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import pl.anaheim.anaitemy.AnaItemy;
import pl.anaheim.anaitemy.items.BalonikItem;

import java.util.UUID;

public class BalonikListener implements Listener {

    private final AnaItemy plugin;

    private static final String BALLOON_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTFiZTQ0ZTg0ZjAxMmY0M2ZhODExNzI3ZDJkNzQ2YTEwYjc1ZGQ5MjQzNzZkZDgwZmJjYjE3NzY4M2QzNTNjZSJ9fX0=";
    private static final UUID BALLOON_PROFILE_UUID = UUID.fromString("C3C4C5C6-D7D8-E9E0-F1F2-A3A4A5A6A7A8");
    private static final int MAX_HEIGHT = 400;
    private static final double SPEED = 5.0;

    public BalonikListener(AnaItemy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!BalonikItem.isBalonik(item)) return;

        event.setCancelled(true);

        // ✅ 4s protection - gracz nie może być spamowany balonikami
        if (plugin.getItemProtectionManager().isProtected(player, "balonik")) {
            return;
        }

        // ✅ Nałóż ochronę
        plugin.getItemProtectionManager().applyProtection(player, "balonik");

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,
                SoundCategory.PLAYERS, 1.5f, 1.0f);

        Location spawnLoc = player.getLocation().clone().add(0, 0.5, 0);

        ArmorStand balloon = player.getWorld().spawn(spawnLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSmall(false);
            stand.setMarker(true);
            stand.setCustomNameVisible(false);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            PlayerProfile profile = Bukkit.createProfile(BALLOON_PROFILE_UUID, "Balloon");
            profile.setProperty(new ProfileProperty("textures", BALLOON_TEXTURE));
            meta.setPlayerProfile(profile);

            head.setItemMeta(meta);
            stand.getEquipment().setHelmet(head);
        });

        new BukkitRunnable() {
            int blocksUp = 0;
            double currentY = spawnLoc.getY();
            final int blockX = spawnLoc.getBlockX();
            final int blockZ = spawnLoc.getBlockZ();
            final World world = spawnLoc.getWorld();
            final double yPerTick = SPEED / 20.0;

            @Override
            public void run() {
                if (!balloon.isValid() || balloon.isDead()) { cancel(); return; }
                if (blocksUp >= MAX_HEIGHT) { balloon.remove(); cancel(); return; }
                if (currentY >= world.getMaxHeight()) { balloon.remove(); cancel(); return; }

                currentY += yPerTick;
                Location newLoc = new Location(world, spawnLoc.getX(), currentY, spawnLoc.getZ());
                balloon.teleport(newLoc);

                int checkY = (int) Math.ceil(currentY) + 1;

                for (int dy = 0; dy <= 1; dy++) {
                    int targetY = checkY + dy;
                    if (targetY >= world.getMaxHeight()) continue;

                    Block block = world.getBlockAt(blockX, targetY, blockZ);

                    if (block.getType() != Material.AIR
                            && block.getType() != Material.CAVE_AIR
                            && block.getType() != Material.VOID_AIR
                            && block.getType() != Material.BEDROCK) {

                        world.spawnParticle(Particle.BLOCK,
                                block.getLocation().add(0.5, 0.5, 0.5),
                                10, 0.3, 0.3, 0.3, 0.1,
                                block.getBlockData());

                        block.breakNaturally();
                    }
                }

                blocksUp++;

                if (blocksUp % 4 == 0) {
                    world.spawnParticle(Particle.END_ROD,
                            newLoc.clone().add(0, 0.5, 0),
                            3, 0.1, 0.1, 0.1, 0.02);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
