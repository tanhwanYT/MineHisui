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

public class HisuiESkill {

    private final JavaPlugin plugin;
    private final HisuiManager manager;

    // 밸런스 값
    private static final double DASH_DAMAGE = 6.0;
    private static final double FINISH_DAMAGE = 7.5;

    private static final double BACKSTEP_DISTANCE = 0.9;   // 뒤로 빠지는 거리
    private static final long PREPARE_TICKS = 4L;          // 뒤로 빠진 후 잠깐 자세 잡는 시간
    private static final int DASH_TICKS = 6;               // 앞으로 돌진하는 틱 수
    private static final double DASH_STEP = 0.95;          // 틱당 전진 거리

    private static final double PATH_HIT_RADIUS = 1.5;     // 돌진 경로 타격 범위
    private static final double FINISH_RANGE = 3.2;        // 도착 지점 초승달 길이
    private static final double FINISH_ANGLE = 120.0;      // 초승달 각도
    private static final double FINISH_Y_RANGE = 2.5;

    public HisuiESkill(JavaPlugin plugin, HisuiManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void cast(Player player) {
        World world = player.getWorld();
        Location start = player.getLocation();

        // 1) 뒤로 회피
        Vector back = start.getDirection().normalize().multiply(-BACKSTEP_DISTANCE);
        Location backLoc = safeGroundLocation(start.clone().add(back), start);
        player.teleport(backLoc);

        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
        world.spawnParticle(Particle.CLOUD, player.getLocation().clone().add(0, 0.2, 0), 12, 0.2, 0.1, 0.2, 0.02);

        // 2) 잠깐 자세 잡고
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) return;

                dashForward(player);
            }
        }.runTaskLater(plugin, PREPARE_TICKS);
    }

    private void dashForward(Player player) {
        World world = player.getWorld();
        Set<LivingEntity> damaged = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancel();
                    return;
                }

                Vector forward = player.getLocation().getDirection().normalize();
                Location next = player.getLocation().clone().add(forward.multiply(DASH_STEP));
                Location safe = safeGroundLocation(next, player.getLocation());

                player.teleport(safe);

                // 경로 타격
                hitPath(player, safe, PATH_HIT_RADIUS, DASH_DAMAGE, damaged);

                // 이펙트
                world.spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().clone().add(0, 1.0, 0), 1, 0.15, 0.15, 0.15, 0.0);
                world.spawnParticle(Particle.CRIT, player.getLocation().clone().add(0, 1.0, 0), 4, 0.25, 0.25, 0.25, 0.0);

                ticks++;
                if (ticks >= DASH_TICKS) {
                    // 도착 지점 초승달 타격
                    boolean hit = hitFinishCrescent(player, FINISH_RANGE, FINISH_ANGLE, FINISH_Y_RANGE, FINISH_DAMAGE, damaged);

                    world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
                    spawnFinishEffect(player);

                    if (hit) {
                        manager.addIaiStack(player, 1);
                    }

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void hitPath(Player caster, Location center, double radius, double damage, Set<LivingEntity> damaged) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target.equals(caster)) continue;
            if (damaged.contains(target)) continue;

            if (center.distanceSquared(target.getLocation()) <= radius * radius) {
                target.damage(damage, caster);
                damaged.add(target);
            }
        }
    }

    private boolean hitFinishCrescent(Player caster, double range, double angleDeg, double yRange, double damage, Set<LivingEntity> damaged) {
        boolean hitAny = false;

        Location origin = caster.getLocation();
        Vector forward = origin.getDirection().normalize();

        double halfAngleRad = Math.toRadians(angleDeg / 2.0);
        double cosThreshold = Math.cos(halfAngleRad);

        for (Entity entity : origin.getWorld().getNearbyEntities(origin, range, yRange, range)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target.equals(caster)) continue;
            if (damaged.contains(target)) continue;

            Vector toTarget = target.getLocation().toVector().subtract(origin.toVector());
            double distance = toTarget.length();
            if (distance > range || distance <= 0.01) continue;

            Vector dirToTarget = toTarget.clone().normalize();
            double dot = forward.dot(dirToTarget);

            if (dot >= cosThreshold) {
                target.damage(damage, caster);
                damaged.add(target);
                hitAny = true;
            }
        }

        return hitAny;
    }

    private void spawnFinishEffect(Player player) {
        World world = player.getWorld();
        Location origin = player.getLocation().clone().add(0, 1.0, 0);
        Vector forward = origin.getDirection().normalize();

        for (double d = 0.8; d <= FINISH_RANGE; d += 0.35) {
            double sideScale = 1.0 - (d / FINISH_RANGE) * 0.55;

            Vector right = getRightVector(forward).multiply(sideScale);
            Location center = origin.clone().add(forward.clone().multiply(d));

            Location leftArc = center.clone().add(right.clone().multiply(-1.2));
            Location midArc = center.clone();
            Location rightArc = center.clone().add(right.clone().multiply(1.2));

            world.spawnParticle(Particle.SWEEP_ATTACK, leftArc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SWEEP_ATTACK, midArc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SWEEP_ATTACK, rightArc, 1, 0, 0, 0, 0);

            world.spawnParticle(Particle.CRIT, leftArc, 2, 0.08, 0.08, 0.08, 0.0);
            world.spawnParticle(Particle.CRIT, midArc, 2, 0.08, 0.08, 0.08, 0.0);
            world.spawnParticle(Particle.CRIT, rightArc, 2, 0.08, 0.08, 0.08, 0.0);
        }
    }

    private Vector getRightVector(Vector forward) {
        Vector flat = forward.clone().setY(0).normalize();
        return new Vector(-flat.getZ(), 0, flat.getX()).normalize();
    }

    private Location safeGroundLocation(Location target, Location fallback) {
        Location loc = target.clone();

        // 너무 허공/벽 속으로 들어가는 것 방지
        if (!loc.getBlock().isPassable()) {
            return fallback.clone();
        }

        Location head = loc.clone().add(0, 1, 0);
        if (!head.getBlock().isPassable()) {
            return fallback.clone();
        }

        return loc;
    }
}