package my.pkg;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class HisuiQSkill {

    private final JavaPlugin plugin;
    private final HisuiManager manager;

    // 밸런스 값
    private static final double FIRST_HIT_DAMAGE = 5.0;
    private static final double SECOND_HIT_DAMAGE = 7.0;

    private static final double CIRCLE_RADIUS = 3.0;

    private static final double FRONT_RANGE = 4.5;
    private static final double FRONT_ANGLE = 90.0; // 전체 각도
    private static final double FRONT_Y_RANGE = 2.5;

    public HisuiQSkill(JavaPlugin plugin, HisuiManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void cast(Player player) {
        World world = player.getWorld();
        Location center = player.getLocation();

        // 1타: 원형 범위
        Set<LivingEntity> firstHitTargets = hitCircle(player, center, CIRCLE_RADIUS, FIRST_HIT_DAMAGE);

        world.playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.95f);
        spawnCircleEffect(center, CIRCLE_RADIUS);

        // 2타: 약간 텀 두고 전방 베기
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<LivingEntity> secondHitTargets = hitFrontCone(player, FRONT_RANGE, FRONT_ANGLE, FRONT_Y_RANGE, SECOND_HIT_DAMAGE);

                world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
                spawnFrontSlashEffect(player, FRONT_RANGE);

                // 스킬 적중 시 거합 중첩 증가 테스트용
                if (!firstHitTargets.isEmpty() || !secondHitTargets.isEmpty()) {
                    manager.addIaiStack(player, 1);
                }
            }
        }.runTaskLater(plugin, 4L); // 0.2초 뒤 2타
    }

    private Set<LivingEntity> hitCircle(Player caster, Location center, double radius, double damage) {
        Set<LivingEntity> hit = new HashSet<>();

        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target.equals(caster)) continue;
            if (!isActuallyInsideRadius(center, target.getLocation(), radius)) continue;

            target.damage(damage, caster);
            hit.add(target);
        }

        return hit;
    }

    private Set<LivingEntity> hitFrontCone(Player caster, double range, double angleDeg, double yRange, double damage) {
        Set<LivingEntity> hit = new HashSet<>();

        Location origin = caster.getLocation();
        Vector forward = origin.getDirection().normalize();

        double halfAngleRad = Math.toRadians(angleDeg / 2.0);
        double cosThreshold = Math.cos(halfAngleRad);

        for (Entity entity : origin.getWorld().getNearbyEntities(origin, range, yRange, range)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target.equals(caster)) continue;

            Vector toTarget = target.getLocation().toVector().subtract(origin.toVector());
            double distance = toTarget.length();
            if (distance > range || distance <= 0.01) continue;

            Vector dirToTarget = toTarget.clone().normalize();
            double dot = forward.dot(dirToTarget);

            if (dot >= cosThreshold) {
                target.damage(damage, caster);
                hit.add(target);
            }
        }

        return hit;
    }

    private boolean isActuallyInsideRadius(Location center, Location target, double radius) {
        if (!center.getWorld().equals(target.getWorld())) return false;
        return center.distanceSquared(target) <= radius * radius;
    }

    private void spawnCircleEffect(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return;

        for (double t = 0; t < Math.PI * 2; t += Math.PI / 12) {
            double x = Math.cos(t) * radius;
            double z = Math.sin(t) * radius;
            Location point = center.clone().add(x, 0.2, z);
            world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.CRIT, point, 2, 0.08, 0.05, 0.08, 0.0);
        }
    }

    private void spawnFrontSlashEffect(Player player, double range) {
        World world = player.getWorld();
        Location origin = player.getLocation().clone().add(0, 1.0, 0);
        Vector forward = origin.getDirection().normalize();

        for (double d = 1.0; d <= range; d += 0.5) {
            Location point = origin.clone().add(forward.clone().multiply(d));
            world.spawnParticle(Particle.SWEEP_ATTACK, point, 1, 0.15, 0.15, 0.15, 0.0);
            world.spawnParticle(Particle.CRIT, point, 3, 0.2, 0.2, 0.2, 0.0);
        }
    }
}