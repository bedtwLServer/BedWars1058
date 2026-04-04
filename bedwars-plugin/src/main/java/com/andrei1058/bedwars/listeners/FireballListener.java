package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.LastHit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.*;

import static com.andrei1058.bedwars.BedWars.config;
import static com.andrei1058.bedwars.BedWars.getAPI;

public class FireballListener implements Listener {

    private final double fireballExplosionSize;
    private final boolean fireballMakeFire;
    private final double fireballHorizontal;
    private final double fireballVertical;

    private final double damageSelf;
    private final double damageEnemy;
    private final double damageTeammates;

    public FireballListener() {
        this.fireballExplosionSize = config.getYml().getDouble(ConfigPath.GENERAL_FIREBALL_EXPLOSION_SIZE);
        this.fireballMakeFire = config.getYml().getBoolean(ConfigPath.GENERAL_FIREBALL_MAKE_FIRE);
        this.fireballHorizontal = config.getYml().getDouble(ConfigPath.GENERAL_FIREBALL_KNOCKBACK_HORIZONTAL) * -1;
        this.fireballVertical = config.getYml().getDouble(ConfigPath.GENERAL_FIREBALL_KNOCKBACK_VERTICAL);

        this.damageSelf = config.getYml().getDouble(ConfigPath.GENERAL_FIREBALL_DAMAGE_SELF);
        this.damageEnemy = config.getYml().getDouble(ConfigPath.GENERAL_FIREBALL_DAMAGE_ENEMY);
        this.damageTeammates = config.getYml().getDouble(ConfigPath.GENERAL_FIREBALL_DAMAGE_TEAMMATES);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void fireballHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Fireball)) return;
        Location location = e.getEntity().getLocation();

        ProjectileSource projectileSource = e.getEntity().getShooter();
        if(!(projectileSource instanceof Player)) return;
        Player source = (Player) projectileSource;

        IArena arena = Arena.getArenaByPlayer(source);
        if (arena == null) return;

        Vector vector = location.toVector();
        World world = location.getWorld();

        assert world != null;
        Collection<Entity> nearbyEntities = world
                .getNearbyEntities(location, fireballExplosionSize, fireballExplosionSize, fireballExplosionSize);

        for (Entity entity : nearbyEntities) {
            if (!(entity instanceof Player)) continue;
            Player player = (Player) entity;
            if (!getAPI().getArenaUtil().isPlaying(player)) continue;

            // Apply knockback
            Vector playerVector = player.getLocation().toVector();
            Vector normalizedVector = vector.subtract(playerVector).normalize();
            Vector horizontalVector = normalizedVector.multiply(fireballHorizontal);
            double y = normalizedVector.getY();
            if (y < 0 ) y += 1.5;
            if (y <= 0.5) {
                y = fireballVertical * 1.5; // kb for not jumping
            } else {
                y = y * fireballVertical * 1.5; // kb for jumping
            }
            player.setVelocity(horizontalVector.setY(y));

            // Track last hit for death attribution
            LastHit lh = LastHit.getLastHit(player);
            if (lh != null) {
                lh.setDamager(source);
                lh.setTime(System.currentTimeMillis());
            } else {
                new LastHit(player, source, System.currentTimeMillis());
            }
            // Determine damage based on relationship
            double damage = 0;
            if (player.equals(source)) {
                damage = damageSelf;
            } else if (arena.getTeam(player).equals(arena.getTeam(source))) {
                damage = damageTeammates;
            } else {
                damage = damageEnemy;
            }
            Bukkit.getLogger().info(String.valueOf(damage));

            // Apply damage even if it's 0 or negative (for knockback-only fireballs)
            // Only skip if damage is explicitly less than 0 (disabled)
            if (damage >= 0) {
                player.damage(damage, source);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void fireballDirectHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Fireball)) return;
        if (!(e.getEntity() instanceof Player)) return;

        if(Arena.getArenaByPlayer((Player) e.getEntity()) == null) return;

        e.setCancelled(true);
    }

    @EventHandler
    public void fireballPrime(ExplosionPrimeEvent e) {
        if (!(e.getEntity() instanceof Fireball)) return;
        ProjectileSource shooter = ((Fireball)e.getEntity()).getShooter();
        if (!(shooter instanceof Player)) return;
        Player player = (Player) shooter;

        if (Arena.getArenaByPlayer(player) == null) return;

        e.setFire(fireballMakeFire);
    }

}