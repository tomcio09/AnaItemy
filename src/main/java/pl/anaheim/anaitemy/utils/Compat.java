package pl.anaheim.anaitemy.utils;

import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;

/**
 * Klasa kompatybilności dla zmian API w 1.21.4
 */
public class Compat {

    // ==================== ENCHANTMENTS ====================
    public static final Enchantment UNBREAKING = Enchantment.UNBREAKING;
    public static final Enchantment EFFICIENCY = Enchantment.EFFICIENCY;
    public static final Enchantment SHARPNESS = Enchantment.SHARPNESS;
    public static final Enchantment FIRE_ASPECT = Enchantment.FIRE_ASPECT;
    public static final Enchantment POWER = Enchantment.POWER;
    public static final Enchantment FLAME = Enchantment.FLAME;
    public static final Enchantment INFINITY = Enchantment.INFINITY;
    public static final Enchantment KNOCKBACK = Enchantment.KNOCKBACK;
    public static final Enchantment MENDING = Enchantment.MENDING;
    public static final Enchantment QUICK_CHARGE = Enchantment.QUICK_CHARGE;

    // 1.21.4 nowe nazwy enchantów
    public static final Enchantment DURABILITY = Enchantment.UNBREAKING;
    public static final Enchantment DAMAGE_ALL = Enchantment.SHARPNESS;
    public static final Enchantment DIG_SPEED = Enchantment.EFFICIENCY;
    public static final Enchantment PROTECTION_PROJECTILE = Enchantment.PROJECTILE_PROTECTION;
    public static final Enchantment PROTECTION_FIRE = Enchantment.FIRE_PROTECTION;
    public static final Enchantment PROTECTION_ENVIRONMENTAL = Enchantment.PROTECTION;
    public static final Enchantment WATER_WORKER = Enchantment.AQUA_AFFINITY;
    public static final Enchantment OXYGEN = Enchantment.RESPIRATION;
    public static final Enchantment ARROW_DAMAGE = Enchantment.POWER;
    public static final Enchantment ARROW_FIRE = Enchantment.FLAME;
    public static final Enchantment ARROW_INFINITE = Enchantment.INFINITY;

    // ==================== POTION EFFECTS ====================
    public static final PotionEffectType FAST_DIGGING = PotionEffectType.HASTE;
    public static final PotionEffectType SLOW = PotionEffectType.SLOWNESS;
    public static final PotionEffectType SLOW_DIGGING = PotionEffectType.MINING_FATIGUE;
    public static final PotionEffectType INCREASE_DAMAGE = PotionEffectType.STRENGTH;
    public static final PotionEffectType DAMAGE_RESISTANCE = PotionEffectType.RESISTANCE;
    public static final PotionEffectType CONFUSION = PotionEffectType.NAUSEA;

    // ==================== PARTICLES ====================
    public static final Particle WATER_SPLASH = Particle.SPLASH;
    public static final Particle SPELL_WITCH = Particle.WITCH;
    public static final Particle SMOKE_LARGE = Particle.LARGE_SMOKE;
    public static final Particle EXPLOSION_LARGE = Particle.EXPLOSION;
    public static final Particle BLOCK_CRACK = Particle.BLOCK;

    // ==================== ATTRIBUTES ====================
    public static final Attribute GENERIC_MAX_HEALTH = Attribute.MAX_HEALTH;
    public static final Attribute GENERIC_ATTACK_DAMAGE = Attribute.ATTACK_DAMAGE;
    public static final Attribute GENERIC_ATTACK_SPEED = Attribute.ATTACK_SPEED;
    public static final Attribute GENERIC_MOVEMENT_SPEED = Attribute.MOVEMENT_SPEED;
    public static final Attribute GENERIC_SCALE = Attribute.SCALE;
    public static final Attribute HORSE_JUMP_STRENGTH = Attribute.JUMP_STRENGTH;
}
