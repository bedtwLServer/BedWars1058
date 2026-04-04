/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.generator.IGenerator;
import com.andrei1058.bedwars.api.arena.shop.ShopHolo;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.entity.Despawnable;
import com.andrei1058.bedwars.api.events.player.PlayerInvisibilityPotionEvent;
import com.andrei1058.bedwars.api.events.player.PlayerKillEvent;
import com.andrei1058.bedwars.api.events.team.TeamEliminatedEvent;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.server.ServerType;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.arena.LastHit;
import com.andrei1058.bedwars.arena.SetupSession;
import com.andrei1058.bedwars.arena.team.BedWarsTeam;
import com.andrei1058.bedwars.configuration.Sounds;
import com.andrei1058.bedwars.listeners.dropshandler.PlayerDrops;
import com.andrei1058.bedwars.support.paper.TeleportManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.andrei1058.bedwars.BedWars.*;
import static com.andrei1058.bedwars.api.language.Language.getMsg;
import static com.andrei1058.bedwars.arena.LastHit.getLastHit;

public class DamageDeathMove implements Listener {

    private final double tntJumpBarycenterAlterationInY;
    private final double tntJumpStrengthReductionConstant;
    private final double tntJumpYAxisReductionConstant;
    private final double tntDamageSelf;
    private final double tntDamageTeammates;
    private final double tntDamageOthers;

    public DamageDeathMove() {
        this.tntJumpBarycenterAlterationInY = config.getYml().getDouble(ConfigPath.GENERAL_TNT_JUMP_BARYCENTER_IN_Y);
        this.tntJumpStrengthReductionConstant = config.getYml().getDouble(ConfigPath.GENERAL_TNT_JUMP_STRENGTH_REDUCTION);
        this.tntJumpYAxisReductionConstant = config.getYml().getDouble(ConfigPath.GENERAL_TNT_JUMP_Y_REDUCTION);
        this.tntDamageSelf = config.getYml().getDouble(ConfigPath.GENERAL_TNT_JUMP_DAMAGE_SELF);
        this.tntDamageTeammates = config.getYml().getDouble(ConfigPath.GENERAL_TNT_JUMP_DAMAGE_TEAMMATES);
        this.tntDamageOthers = config.getYml().getDouble(ConfigPath.GENERAL_TNT_JUMP_DAMAGE_OTHERS);
    }

    /**
     * Unified damage event handler
     * Handles all types of damage including player damage, entity damage, and fatal damage
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        // Handle non-player entities
        if (!(e.getEntity() instanceof Player)) {
            handleNonPlayerDamage(e);
            return;
        }
        Bukkit.getLogger().info(String.valueOf(e.getDamage()));
        Bukkit.getLogger().info(String.valueOf(e.getFinalDamage()));
        Bukkit.getLogger().info(e.getCause().name());

        Player victim = (Player) e.getEntity();
        IArena a = Arena.getArenaByPlayer(victim);

        // Handle lobby world damage
        if (BedWars.getServerType() == ServerType.MULTIARENA) {
            if (e.getEntity().getLocation().getWorld().getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
                e.setCancelled(true);
                return;
            }
        }

        // No arena - no changes
        if (a == null) {
            return;
        }

        // Handle spectators and non-playing states
        if (a.isSpectator(victim) || a.isReSpawning(victim) || a.getStatus() != GameState.playing) {
            e.setCancelled(true);
            return;
        }

        // Handle respawn invulnerability
        if (BedWarsTeam.reSpawnInvulnerability.containsKey(victim.getUniqueId())) {
            if (BedWarsTeam.reSpawnInvulnerability.get(victim.getUniqueId()) > System.currentTimeMillis()) {
                e.setCancelled(true);
                return;
            } else {
                BedWarsTeam.reSpawnInvulnerability.remove(victim.getUniqueId());
            }
        }

        // Handle entity-caused damage
        if (e instanceof EntityDamageByEntityEvent) {
            handleEntityDamage((EntityDamageByEntityEvent) e, victim, a);

            // If event was cancelled, don't proceed
            if (e.isCancelled()) {
                return;
            }
        }
        // Check for fatal damage ONLY if damage wasn't cancelled
        if (victim.getHealth() <= e.getFinalDamage()) {
            e.setCancelled(true);
            handleDeath(victim, e);
        }
    }
    /**
     * Shows player health on bow hit
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowHit(EntityDamageByEntityEvent e) {
        if (e.getEntity().getType() != EntityType.PLAYER) return;
        if (!(e.getDamager() instanceof Projectile)) return;
        Projectile projectile = (Projectile) e.getDamager();
        if (projectile.getShooter() == null) return;
        if (!(projectile.getShooter() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        Player damager = (Player) projectile.getShooter();
        IArena a = Arena.getArenaByPlayer(p);
        if (a == null) return;
        if (a.getStatus() != GameState.playing) return;

        // projectile hit message #696, #711
        ITeam team = a.getTeam(p);
        Language lang = Language.getPlayerLanguage(damager);
        if (lang.m(Messages.PLAYER_HIT_BOW).isEmpty()) return;
        String message = lang.m(Messages.PLAYER_HIT_BOW)
                .replace("{amount}", new DecimalFormat("00.#").format(((Player) e.getEntity()).getHealth() - e.getFinalDamage()))
                .replace("{TeamColor}", team.getColor().chat().toString())
                .replace("{TeamName}", team.getDisplayName(lang))
                .replace("{PlayerName}", ChatColor.stripColor(p.getDisplayName()));
        damager.sendMessage(message);
    }

    /**
     * Handles entity-caused damage
     */
    private void handleEntityDamage(EntityDamageByEntityEvent e, Player victim, IArena a) {
        Bukkit.getLogger().info(e.getDamager().getType().name());
        Entity damagerEntity = e.getDamager();

        // Handle despawnable entities (Golems, Silverfish)
        if (nms.isDespawnable(damagerEntity)) {
            handleDespawnableDamage(e, a);
            return;
        }

        Player damager = getDamagerFromEvent(e);

        // Handle TNT damage
        if (damagerEntity instanceof TNTPrimed) {
            handleTNTDamage((TNTPrimed) damagerEntity, victim, e, a, damager);
            return;
        }

        // Handle silverfish and iron golem damage
        if ((damagerEntity instanceof Silverfish) || (damagerEntity instanceof IronGolem)) {
            LastHit lh = LastHit.getLastHit(victim);
            if (lh != null) {
                lh.setDamager(damagerEntity);
                lh.setTime(System.currentTimeMillis());
            } else {
                new LastHit(victim, damagerEntity, System.currentTimeMillis());
            }
            return;
        }

        if (damager == null) {
            return;
        }

        // Damager validation
        if (a.isSpectator(damager) || a.isReSpawning(damager.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        // Team-mate damage
        if (a.getTeam(victim).equals(a.getTeam(damager))) {
            if (!(damagerEntity instanceof TNTPrimed)) {
                e.setCancelled(true);
            }
            return;
        }

        // protection after re-spawn
        if (BedWarsTeam.reSpawnInvulnerability.containsKey(victim.getUniqueId())) {
            if (BedWarsTeam.reSpawnInvulnerability.get(victim.getUniqueId()) > System.currentTimeMillis()) {
                e.setCancelled(true);
                return;
            } else {
                BedWarsTeam.reSpawnInvulnerability.remove(victim.getUniqueId());
            }
        }

        // but if the damageR is the re-spawning player remove protection
        BedWarsTeam.reSpawnInvulnerability.remove(damager.getUniqueId());

        // Track last hit for death attribution
        LastHit lh = LastHit.getLastHit(victim);
        if (lh != null) {
            lh.setDamager(damager);
            lh.setTime(System.currentTimeMillis());
        } else {
            new LastHit(victim, damager, System.currentTimeMillis());
        }

        // Handle invisibility removal on damage
        handleInvisibilityRemoval(victim, a);
    }

    /**
     * Handles TNT explosion damage
     */
    private void handleTNTDamage(TNTPrimed tnt, Player victim, EntityDamageByEntityEvent e, IArena a, Player damager) {
        if (tnt.getSource() == null) {
            return;
        }

        if (!(tnt.getSource() instanceof Player)) {
            return;
        }

        Player tntSource = (Player) tnt.getSource();

        if (tntSource.equals(victim)) {
            // Self damage
            if (tntDamageSelf > -1) {
                e.setDamage(tntDamageSelf);
            }
            // tnt jump. credits to feargames.it
            applyTNTJumpForce(victim, tnt);
        } else {
            // Team or enemy damage
            ITeam currentTeam = a.getTeam(victim);
            ITeam damagerTeam = a.getTeam(tntSource);

            if (currentTeam.equals(damagerTeam)) {
                if (tntDamageTeammates > -1) {
                    e.setDamage(tntDamageTeammates);
                }
            } else {
                if (tntDamageOthers > -1) {
                    e.setDamage(tntDamageOthers);
                }
            }
        }
    }

    /**
     * Applies TNT jump force to player
     */
    private void applyTNTJumpForce(Player victim, TNTPrimed tnt) {
        LivingEntity damaged = victim;
        Vector distance = damaged.getLocation()
                .subtract(0, tntJumpBarycenterAlterationInY, 0)
                .toVector()
                .subtract(tnt.getLocation().toVector());
        Vector direction = distance.clone().normalize();
        double force = ((tnt.getYield() * tnt.getYield()) / (tntJumpStrengthReductionConstant + distance.length()));
        Vector resultingForce = direction.clone().multiply(force);
        resultingForce.setY(resultingForce.getY() / (distance.length() + tntJumpYAxisReductionConstant));
        damaged.setVelocity(resultingForce);
    }

    /**
     * Handles damage to despawnable entities (Golems, Silverfish)
     */
    private void handleDespawnableDamage(EntityDamageByEntityEvent e, IArena a) {
        Player damager = getDamagerFromEvent(e);

        if (damager == null || !a.isPlayer(damager)) {
            e.setCancelled(true);
            return;
        }

        // Do not hurt own mobs
        if (a.getTeam(damager) == nms.getDespawnablesList().get(e.getEntity().getUniqueId()).getTeam()) {
            e.setCancelled(true);
        }
    }

    /**
     * Extracts damager from various event sources
     */
    private Player getDamagerFromEvent(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player) {
            return (Player) e.getDamager();
        } else if (e.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) e.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        } else if (e.getDamager() instanceof TNTPrimed) {
            TNTPrimed tnt = (TNTPrimed) e.getDamager();
            if (tnt.getSource() instanceof Player) {
                return (Player) tnt.getSource();
            }
        }
        return null;
    }

    /**
     * Handles invisibility potion removal when player takes damage
     */
    private void handleInvisibilityRemoval(Player victim, IArena a) {
        if (a.getShowTime().containsKey(victim)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player on : a.getWorld().getPlayers()) {
                    BedWars.nms.showArmor(victim, on);
                }
                a.getShowTime().remove(victim);
                victim.removePotionEffect(PotionEffectType.INVISIBILITY);
                ITeam team = a.getTeam(victim);
                victim.sendMessage(getMsg(victim, Messages.INTERACT_INVISIBILITY_REMOVED_DAMGE_TAKEN));
                Bukkit.getPluginManager().callEvent(new PlayerInvisibilityPotionEvent(PlayerInvisibilityPotionEvent.Type.REMOVED, team, victim, a));
            });
        }
    }

    /**
     * Handles non-player entity damage
     */
    private void handleNonPlayerDamage(EntityDamageEvent e) {
        if (BedWars.getServerType() == ServerType.MULTIARENA) {
            if (e.getEntity().getLocation().getWorld().getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        IArena a = Arena.getArenaByPlayer(e.getPlayer());
        if (a == null) {
            SetupSession ss = SetupSession.getSession(e.getPlayer().getUniqueId());
            if (ss != null) {
                e.setRespawnLocation(e.getPlayer().getWorld().getSpawnLocation());
            }
        } else {
            if (a.isSpectator(e.getPlayer())) {
                e.setRespawnLocation(a.getSpectatorLocation());
                String iso = Language.getPlayerLanguage(e.getPlayer()).getIso();
                for (IGenerator o : a.getOreGenerators()) {
                    o.updateHolograms(e.getPlayer(), iso);
                }
                for (ITeam t : a.getTeams()) {
                    for (IGenerator o : t.getGenerators()) {
                        o.updateHolograms(e.getPlayer(), iso);
                    }
                }
                for (ShopHolo sh : ShopHolo.getShopHolo()) {
                    if (sh.getA() == a) {
                        sh.updateForPlayer(e.getPlayer(), iso);
                    }
                }
                a.sendSpectatorCommandItems(e.getPlayer());
                return;
            }
            ITeam t = a.getTeam(e.getPlayer());
            if (t == null) {
                e.setRespawnLocation(a.getReSpawnLocation());
                plugin.getLogger().severe(e.getPlayer().getName() + " re-spawn error on " + a.getArenaName() + "[" + a.getWorldName() + "] because the team was NULL and he was not spectating!");
                plugin.getLogger().severe("This is caused by one of your plugins: remove or configure any re-spawn related plugins.");
                a.removePlayer(e.getPlayer(), false);
                a.removeSpectator(e.getPlayer(), false);
                return;
            }
            if (t.isBedDestroyed()) {
                e.setRespawnLocation(a.getSpectatorLocation());
                a.addSpectator(e.getPlayer(), true, null);
                t.getMembers().remove(e.getPlayer());
                e.getPlayer().sendMessage(getMsg(e.getPlayer(), Messages.PLAYER_DIE_ELIMINATED_CHAT));
                if (t.getMembers().isEmpty()) {
                    Bukkit.getPluginManager().callEvent(new TeamEliminatedEvent(a, t));
                    for (Player p : a.getWorld().getPlayers()) {
                        p.sendMessage(getMsg(p, Messages.TEAM_ELIMINATED_CHAT).replace("{TeamColor}", t.getColor().chat().toString()).replace("{TeamName}", t.getDisplayName(Language.getPlayerLanguage(p))));
                    }
                    Bukkit.getScheduler().runTaskLater(plugin, a::checkWinner, 40L);
                }
            } else {
                //respawn session
                int respawnTime = config.getInt(ConfigPath.GENERAL_CONFIGURATION_RE_SPAWN_COUNTDOWN);
                if (respawnTime > 1) {
                    e.setRespawnLocation(a.getReSpawnLocation());
                    a.startReSpawnSession(e.getPlayer(), respawnTime);
                } else {
                    // instant respawn configuration
                    e.setRespawnLocation(t.getSpawn());
                    t.respawnMember(e.getPlayer());
                }
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (Arena.isInArena(e.getPlayer())) {
            IArena a = Arena.getArenaByPlayer(e.getPlayer());

            // todo check on x y z change... not head rotation because this is really spammy
            if (e.getFrom().getChunk().getX() != e.getTo().getChunk().getX() ||
                    e.getFrom().getChunk().getZ() != e.getTo().getChunk().getZ() ||
                    !e.getFrom().getChunk().getWorld().equals(e.getTo().getChunk().getWorld())
            ) {

                /* update armor-stands hidden by nms */
                String iso = Language.getPlayerLanguage(e.getPlayer()).getIso();
                for (IGenerator o : a.getOreGenerators()) {
                    o.updateHolograms(e.getPlayer(), iso);
                }
                for (ITeam t : a.getTeams()) {
                    for (IGenerator o : t.getGenerators()) {
                        o.updateHolograms(e.getPlayer(), iso);
                    }
                }
                for (ShopHolo sh : ShopHolo.getShopHolo()) {
                    if (sh.getA() == a) {
                        sh.updateForPlayer(e.getPlayer(), iso);
                    }
                }

                // hide armor for those with invisibility potions
                if (!a.getShowTime().isEmpty()) {
                    // generic hide packets
                    for (Map.Entry<Player, Integer> entry : a.getShowTime().entrySet()) {
                        if (entry.getValue() > 1) {
                            BedWars.nms.hideArmor(entry.getKey(), e.getPlayer());
                        }
                    }
                    // if the moving player has invisible armor
                    if (a.getShowTime().containsKey(e.getPlayer())) {
                        for (Player p : a.getPlayers()) {
                            nms.hideArmor(e.getPlayer(), p);
                        }
                    }
                    if (a.getShowTime().containsKey(e.getPlayer())) {
                        for (Player p : a.getSpectators()) {
                            nms.hideArmor(e.getPlayer(), p);
                        }
                    }
                }
            }

            if (a.isSpectator(e.getPlayer()) || a.isReSpawning(e.getPlayer())) {
                if (e.getTo().getY() < 0) {
                    TeleportManager.teleportC(e.getPlayer(), a.isSpectator(e.getPlayer()) ? a.getSpectatorLocation() : a.getReSpawnLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    e.getPlayer().setAllowFlight(true);
                    e.getPlayer().setFlying(true);
                    // how to remove fall velocity?
                }
            } else {
                if (a.getStatus() == GameState.playing) {
                    if (e.getPlayer().getLocation().getBlockY() <= a.getYKillHeight()) {
                        nms.voidKill(e.getPlayer());
                    }
                    for (ITeam t : a.getTeams()) {
                        if (e.getPlayer().getLocation().distance(t.getBed()) < 4) {
                            if (t.isMember(e.getPlayer()) && t instanceof BedWarsTeam) {
                                if (((BedWarsTeam) t).getBedHolo(e.getPlayer()) == null) continue;
                                if (!((BedWarsTeam) t).getBedHolo(e.getPlayer()).isHidden()) {
                                    ((BedWarsTeam) t).getBedHolo(e.getPlayer()).hide();
                                }
                            }
                        } else {
                            if (t.isMember(e.getPlayer()) && t instanceof BedWarsTeam) {
                                if (((BedWarsTeam) t).getBedHolo(e.getPlayer()) == null) continue;
                                if (((BedWarsTeam) t).getBedHolo(e.getPlayer()).isHidden()) {
                                    ((BedWarsTeam) t).getBedHolo(e.getPlayer()).show();
                                }
                            }
                        }
                    }
                    if (e.getFrom() != e.getTo()) {
                        Arena.afkCheck.remove(e.getPlayer().getUniqueId());
                        BedWars.getAPI().getAFKUtil().setPlayerAFK(e.getPlayer(), false);
                    }
                } else {
                    if (e.getPlayer().getLocation().getBlockY() <= 0) {
                        ITeam bwt = a.getTeam(e.getPlayer());
                        if (bwt != null) {
                            TeleportManager.teleport(e.getPlayer(), bwt.getSpawn());
                        } else {
                            TeleportManager.teleport(e.getPlayer(), a.getSpectatorLocation());
                        }
                    }
                }
            }
        } else {
            if (config.getBoolean(ConfigPath.LOBBY_VOID_TELEPORT_ENABLED) && e.getPlayer().getWorld().getName().equalsIgnoreCase(config.getLobbyWorldName()) && BedWars.getServerType() == ServerType.MULTIARENA) {
                if (e.getTo().getY() < config.getInt(ConfigPath.LOBBY_VOID_TELEPORT_HEIGHT)) {
                    TeleportManager.teleportC(e.getPlayer(), config.getConfigLoc("lobbyLoc"), PlayerTeleportEvent.TeleportCause.PLUGIN);
                }
            }
        }
    }

    @EventHandler
    public void onProjHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (proj == null) return;
        if (e.getEntity().getShooter() instanceof Player) {
            IArena a = Arena.getArenaByPlayer((Player) e.getEntity().getShooter());
            if (a != null) {
                if (!a.isPlayer((Player) e.getEntity().getShooter())) return;
                String utility = "";
                if (proj instanceof Snowball) {
                    utility = "silverfish";
                }
                if (!utility.isEmpty()) {
                    spawnUtility(utility, e.getEntity().getLocation(), a.getTeam((Player) e.getEntity().getShooter()), (Player) e.getEntity().getShooter());
                }
            }
        }
    }

    @EventHandler
    public void onItemFrameDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity().getType() == EntityType.ITEM_FRAME) {
            IArena a = Arena.getArenaByIdentifier(e.getEntity().getWorld().getName());
            if (a != null) {
                e.setCancelled(true);
            }
            if (BedWars.getServerType() == ServerType.MULTIARENA) {
                if (BedWars.getLobbyWorld().equals(e.getEntity().getWorld().getName())) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (Arena.getArenaByIdentifier(e.getEntity().getLocation().getWorld().getName()) != null) {
            if (e.getEntityType() == EntityType.IRON_GOLEM || e.getEntityType() == EntityType.SILVERFISH) {
                e.getDrops().clear();
                e.setDroppedExp(0);
            }
        }

        // clean if necessary
        nms.getDespawnablesList().remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == nms.materialCake()) {
            if (Arena.getArenaByIdentifier(e.getPlayer().getWorld().getName()) != null) {
                e.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("unused")
    private static void spawnUtility(String s, Location loc, ITeam t, Player p) {
        if ("silverfish".equalsIgnoreCase(s)) {
            nms.spawnSilverfish(loc, t, shop.getYml().getDouble(ConfigPath.SHOP_SPECIAL_SILVERFISH_SPEED), shop.getYml().getDouble(ConfigPath.SHOP_SPECIAL_SILVERFISH_HEALTH),
                    shop.getInt(ConfigPath.SHOP_SPECIAL_SILVERFISH_DESPAWN),
                    BedWars.shop.getYml().getDouble(ConfigPath.SHOP_SPECIAL_SILVERFISH_DAMAGE));
        }
    }

    /**
     * Handles player death
     */
    public void handleDeath(Player victim, EntityDamageEvent damageEvent) {

        IArena a = Arena.getArenaByPlayer(victim);
        if (a == null) return;
        if (a.isSpectator(victim)) return;
        if (a.getStatus() != GameState.playing) return;

        ITeam victimsTeam = a.getTeam(victim);
        if (victimsTeam == null) return;

        if (a.isReSpawning(victim)) return;

        victim.setHealth(20);
        victim.setFireTicks(0);
        victim.setFallDistance(0);
        victim.setVelocity(new Vector(0, 0, 0));
        BedWars.nms.clearArrowsFromPlayerBody(victim);

        Player killer = victim.getKiller();
        ITeam killersTeam = null;

        LastHit lh = getLastHit(victim);
        if (killer == null && lh != null && lh.getTime() >= System.currentTimeMillis() - 15000) {
            if (lh.getDamager() instanceof Player) {
                killer = (Player) lh.getDamager();
                if (killer.equals(victim)) killer = null;
            }
        }

        if (killer != null) killersTeam = a.getTeam(killer);

        String message = victimsTeam.isBedDestroyed()
                ? Messages.PLAYER_DIE_UNKNOWN_REASON_FINAL_KILL
                : Messages.PLAYER_DIE_UNKNOWN_REASON_REGULAR;

        PlayerKillEvent.PlayerKillCause cause = victimsTeam.isBedDestroyed()
                ? PlayerKillEvent.PlayerKillCause.UNKNOWN_FINAL_KILL
                : PlayerKillEvent.PlayerKillCause.UNKNOWN;

        PlayerKillEvent event = new PlayerKillEvent(
                a, victim, victimsTeam, killer, killersTeam,
                player -> Language.getMsg(player, message),
                cause
        );

        Bukkit.getPluginManager().callEvent(event);

        if (killer != null && event.playSound()) {
            Sounds.playSound(ConfigPath.SOUNDS_KILL, killer);
        }

        if (event.getMessage() != null) {
            for (Player p : a.getPlayers()) {
                Language lang = Language.getPlayerLanguage(p);
                p.sendMessage(event.getMessage().apply(p)
                        .replace("{PlayerColor}", victimsTeam.getColor().chat().toString())
                        .replace("{PlayerName}", victim.getDisplayName())
                        .replace("{PlayerTeamName}", victimsTeam.getDisplayName(lang))
                        .replace("{KillerName}", killer == null ? "" : killer.getDisplayName()));
            }

            for (Player p : a.getSpectators()) {
                Language lang = Language.getPlayerLanguage(p);
                p.sendMessage(event.getMessage().apply(p));
            }
        }

        List<ItemStack> drops = new ArrayList<>();
        if (PlayerDrops.handlePlayerDrops(a, victim, killer, victimsTeam, killersTeam, cause, drops)) {
            drops.clear();
        }

        if (a.isSpectator(victim)) {
            victim.teleport(a.getSpectatorLocation());

            String iso = Language.getPlayerLanguage(victim).getIso();

            for (IGenerator o : a.getOreGenerators()) {
                o.updateHolograms(victim, iso);
            }

            for (ITeam t : a.getTeams()) {
                for (IGenerator o : t.getGenerators()) {
                    o.updateHolograms(victim, iso);
                }
            }

            for (ShopHolo sh : ShopHolo.getShopHolo()) {
                if (sh.getA() == a) {
                    sh.updateForPlayer(victim, iso);
                }
            }

            a.sendSpectatorCommandItems(victim);
            return;
        }

        ITeam t = a.getTeam(victim);

        if (t == null) {
            victim.teleport(a.getReSpawnLocation());

            plugin.getLogger().severe(victim.getName() + " re-spawn error on "
                    + a.getArenaName() + "[" + a.getWorldName() + "] because the team was NULL and he was not spectating!");
            plugin.getLogger().severe("This is caused by one of your plugins: remove or configure any re-spawn related plugins.");

            a.removePlayer(victim, false);
            a.removeSpectator(victim, false);
            return;
        }

        if (t.isBedDestroyed()) {

            victim.teleport(a.getSpectatorLocation());

            a.addSpectator(victim, true, null);
            t.getMembers().remove(victim);

            victim.sendMessage(getMsg(victim, Messages.PLAYER_DIE_ELIMINATED_CHAT));

            if (t.getMembers().isEmpty()) {
                Bukkit.getPluginManager().callEvent(new TeamEliminatedEvent(a, t));

                for (Player on : a.getWorld().getPlayers()) {
                    on.sendMessage(getMsg(on, Messages.TEAM_ELIMINATED_CHAT)
                            .replace("{TeamColor}", t.getColor().chat().toString())
                            .replace("{TeamName}", t.getDisplayName(Language.getPlayerLanguage(on))));
                }

                a.checkWinner();
            }

        } else {

            int respawnTime = config.getInt(ConfigPath.GENERAL_CONFIGURATION_RE_SPAWN_COUNTDOWN);

            if (respawnTime > 1) {

                victim.teleport(a.getReSpawnLocation());
                a.startReSpawnSession(victim, respawnTime);

            } else {

                victim.teleport(t.getSpawn());
                t.respawnMember(victim);
            }
        }

        if (lh != null) lh.setDamager(null);
    }

}