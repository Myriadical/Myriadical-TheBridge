/*
 * TheBridge - Defend your base and try to wipe out the others
 * Copyright (C)  2021  Plugily Projects - maintained by Tigerpanzer_02, 2Wild4You and contributors
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package plugily.projects.thebridge.arena;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.spigotmc.event.entity.EntityDismountEvent;
import plugily.projects.commonsbox.minecraft.compat.VersionUtils;
import plugily.projects.commonsbox.minecraft.compat.events.api.CBEntityPickupItemEvent;
import plugily.projects.commonsbox.minecraft.compat.events.api.CBPlayerPickupArrow;
import plugily.projects.commonsbox.minecraft.compat.xseries.XMaterial;
import plugily.projects.commonsbox.minecraft.compat.xseries.XSound;
import plugily.projects.thebridge.ConfigPreferences;
import plugily.projects.thebridge.Main;
import plugily.projects.thebridge.api.StatsStorage;
import plugily.projects.thebridge.arena.base.Base;
import plugily.projects.thebridge.arena.options.ArenaOption;
import plugily.projects.thebridge.handlers.ChatManager;
import plugily.projects.thebridge.handlers.items.SpecialItem;
import plugily.projects.thebridge.handlers.rewards.Reward;
import plugily.projects.thebridge.kits.level.ArcherKit;
import plugily.projects.thebridge.user.User;
import plugily.projects.thebridge.utils.Debugger;
import plugily.projects.thebridge.utils.Utils;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

/**
 * @author Tigerpanzer_02
 * <p>
 * Created at 23.11.2020
 */
public class ArenaEvents implements Listener {

  private final Main plugin;
  private final ChatManager chatManager;

  public ArenaEvents(Main plugin) {
    this.plugin = plugin;
    chatManager = plugin.getChatManager();
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
  }

  @EventHandler
  public void onArmorStandEject(EntityDismountEvent e) {
    if (!(e.getEntity() instanceof ArmorStand) || !"TheBridgeArmorStand".equals(e.getEntity().getCustomName())) {
      return;
    }
    if (!(e.getDismounted() instanceof Player)) {
      return;
    }
    if (e.getDismounted().isDead()) {
      e.getEntity().remove();
    }
    //we could use setCancelled here but for 1.12 support we cannot (no api)
    e.getDismounted().addPassenger(e.getEntity());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onBlockBreakEvent(BlockBreakEvent event) {
    Player player = event.getPlayer();
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    if (!canBuild(arena, player, event.getBlock().getLocation())) {
      event.setCancelled(true);
      return;
    }
    if (arena.getPlacedBlocks().contains(event.getBlock())) {
      arena.removePlacedBlock(event.getBlock());
      // Does not work?
      event.getBlock().getDrops().clear();
      // Alternative
      event.getBlock().setType(XMaterial.AIR.parseMaterial());
    }
    event.setCancelled(true);
  }

  @EventHandler
  public void onBuild(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    if (!canBuild(arena, player, event.getBlock().getLocation())) {
      event.setCancelled(true);
      return;
    }
    arena.addPlacedBlock(event.getBlock());
  }

  public boolean canBuild(Arena arena, Player player, Location location) {
    if (!arena.getArenaBorder().isIn(player)) {
      player.sendMessage(plugin.getChatManager().colorMessage("In-Game.Messages.Build-Break"));
      return false;
    }
    for (Base base : arena.getBases()) {
      if (base.getBaseCuboid().isIn(location)) {
        player.sendMessage(plugin.getChatManager().colorMessage("In-Game.Messages.Build-Break"));
        return false;
      }
    }
    return true;
  }

  private void rewardLastAttacker(Arena arena, Player victim) {
    if (arena.getHits().containsKey(victim)) {
      Player attacker = arena.getHits().get(victim);
      arena.removeHits(victim);
      plugin.getRewardsHandler().performReward(attacker, Reward.RewardType.KILL);
      plugin.getUserManager().addStat(attacker, StatsStorage.StatisticType.KILLS);
      plugin.getUserManager().addExperience(attacker, 2);
      plugin.getUserManager().getUser(attacker).addStat(StatsStorage.StatisticType.LOCAL_KILLS, 1);
      attacker.sendMessage(plugin.getChatManager().colorMessage("In-Game.Messages.Killed").replace("%VICTIM%", victim.getName()));
      plugin.getChatManager().broadcast(arena, plugin.getChatManager().colorMessage("In-Game.Messages.Death").replace("%PLAYER%", victim.getName()).replace("%ATTACKER%", attacker.getName()));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onHit(EntityDamageByEntityEvent e) {
    if (e.getEntity() instanceof Player && e.getDamager() instanceof Player) {
      Player victim = (Player) e.getEntity();
      Player attack = (Player) e.getDamager();
      if (!ArenaUtils.areInSameArena(victim, attack)) {
        return;
      }
      Arena arena = ArenaRegistry.getArena(victim);
      if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
        return;
      }
      if (plugin.getUserManager().getUser(attack).isSpectator()) {
        e.setCancelled(true);
        return;
      }
      if (arena.isTeammate(attack, victim)) {
        e.setCancelled(true);
        return;
      }
      arena.addHits(victim, attack);
    }
  }

  private final HashMap<Player, Long> cooldownPortal = new HashMap<>();
  private final HashMap<Player, Long> cooldownOutside = new HashMap<>();

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onPlayerMove(PlayerMoveEvent event) {
    Player player = event.getPlayer();
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    if (arena.isResetRound() && !plugin.getUserManager().getUser(player).isSpectator()) {
      if (arena.getBase(event.getPlayer()).getCageCuboid() != null) {
        return;
      }
      if (event.getFrom().getZ() != event.getTo().getZ() && event.getFrom().getX() != event.getTo().getX()) {
        event.setCancelled(true);
        return;
      }
      return;
    }
    if (!arena.inBase(player)) {
      return;
    }
    if (!arena.getArenaBorder().isInWithMarge(player.getLocation(), 5)) {
      if (cooldownOutside.containsKey(player) && cooldownOutside.get(player) <= System.currentTimeMillis() - 1500) {
        cooldownOutside.remove(player);
        return;
      }
      player.damage(100);
      Debugger.debug(Level.INFO, "Killed " + player.getName() + " because he is more than 5 blocks outside arena location, Location: " + player.getLocation() + "; ArenaBorder: " + arena.getArenaBorder().getMinPoint() + ";" + arena.getArenaBorder().getMaxPoint() + ";" + arena.getArenaBorder().getCenter());
      return;
    }
    if (cooldownPortal.containsKey(player)) {
      if (cooldownPortal.get(player) <= System.currentTimeMillis() - 5000) cooldownPortal.remove(player);
      return;
    }
    if (arena.getBase(player).getPortalCuboid().isIn(player)) {
      cooldownPortal.put(player, System.currentTimeMillis());
      player.sendMessage(chatManager.colorMessage("In-Game.Messages.Portal.Own", player));
      //prevent players being stuck on portal location
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (player != null) {
          if (arena.getBase(player).getPortalCuboid().isInWithMarge(player.getLocation(), 1)) {
            player.damage(100);
            Debugger.debug(Level.INFO, "Killed " + player.getName() + " because he is more than 3 seconds on own portal (seems to stuck)");
          }
        }
      }, 20 * 3 /* 3 seconds as cooldown to prevent instant respawning */);
      return;
    }
    for (Base base : arena.getBases()) {
      if (base.getPortalCuboid().isIn(player)) {
        if (base.getPoints() >= arena.getOption(ArenaOption.MODE_VALUE)) {
          cooldownPortal.put(player, System.currentTimeMillis());
          player.sendMessage(chatManager.colorMessage("In-Game.Messages.Portal.Out", player));
          return;
        }
        cooldownPortal.put(player, System.currentTimeMillis());
        arena.resetRound();
        player.teleport(arena.getBase(player).getPlayerSpawnPoint());
        if (arena.getMode() == Arena.Mode.HEARTS) {
          base.addPoint();
        } else if (arena.getMode() == Arena.Mode.POINTS) {
          arena.getBase(player).addPoint();
        }
        String title = chatManager.colorMessage("In-Game.Messages.Portal.Scored.Title").replace("%player%", player.getName()).replace("%base%", arena.getBase(player).getFormattedColor()).replace("%base_jumped%", base.getFormattedColor());
        String subtitle = chatManager.colorMessage("In-Game.Messages.Portal.Scored.Subtitle").replace("%player%", player.getName()).replace("%base%", arena.getBase(player).getFormattedColor()).replace("%base_jumped%", base.getFormattedColor());
        for (Player p : arena.getPlayers()) {
          VersionUtils.sendTitles(p, title, subtitle, 5, 80, 5);
        }
        chatManager.broadcast(arena, chatManager.colorMessage("In-Game.Messages.Portal.Opponent").replace("%player%", player.getName()).replace("%base%", arena.getBase(player).getFormattedColor()).replace("%base_jumped%", base.getFormattedColor()));
        arena.getScoreboardManager().resetBaseCache();
        plugin.getUserManager().addStat(player, StatsStorage.StatisticType.SCORED_POINTS);
        plugin.getUserManager().addExperience(player, 10);
        plugin.getUserManager().getUser(player).addStat(StatsStorage.StatisticType.LOCAL_SCORED_POINTS, 1);
        return;
      }
    }
  }

  @EventHandler
  public void onEntityDamage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player)) {
      return;
    }
    Player victim = (Player) e.getEntity();
    Arena arena = ArenaRegistry.getArena(victim);
    if (arena == null) {
      return;
    }
    switch (e.getCause()) {
      case DROWNING:
        e.setCancelled(true);
        break;
      case FALL:
        if (arena.getBase(victim) != null)
          if (arena.getBase(victim).isDamageCooldown()) {
            e.setCancelled(true);
            break;
          }
        if (!plugin.getConfigPreferences().getOption(ConfigPreferences.Option.DISABLE_FALL_DAMAGE)) {
          if (e.getDamage() >= victim.getHealth()) {
            //kill the player for suicidal death, else do not
            victim.damage(1000.0);
          }
        } else {
          e.setCancelled(true);
        }
        break;
      case VOID:
        //only move to lobby spawn without damage
        if (arena.getArenaState() == ArenaState.STARTING || arena.getArenaState() == ArenaState.WAITING_FOR_PLAYERS) {
          victim.teleport(arena.getLobbyLocation());
          e.setCancelled(true);
        }
        //kill the player and move to the spawn point
        if (arena.getBase(victim) != null) {
          victim.damage(1000.0);
          victim.teleport(arena.getBase(victim).getPlayerRespawnPoint());
        }
        break;
      default:
        break;
    }
  }

  @EventHandler
  public void onBowShot(EntityShootBowEvent e) {
    if (!(e.getEntity() instanceof Player)) {
      return;
    }
    User user = plugin.getUserManager().getUser((Player) e.getEntity());
    Arena arena = ArenaRegistry.getArena(user.getPlayer());
    if (arena == null) {
      return;
    }
    if (arena.isResetRound()) {
      e.setCancelled(true);
      return;
    }
    if (user.getCooldown("bow_shot") == 0) {
      int cooldown = 5;
      if ((user.getKit() instanceof ArcherKit)) {
        cooldown = 3;
      }
      user.setCooldown("bow_shot", plugin.getConfig().getInt("Bow-Cooldown", cooldown));
      Player player = (Player) e.getEntity();
      Utils.applyActionBarCooldown(player, plugin.getConfig().getInt("Bow-Cooldown", cooldown));
      VersionUtils.setDurability(e.getBow(), (short) 0);
    } else {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onArrowPickup(CBPlayerPickupArrow e) {
    if (ArenaRegistry.isInArena(e.getPlayer())) {
      e.getItem().remove();
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onItemPickup(CBEntityPickupItemEvent e) {
    if (!(e.getEntity() instanceof Player)) {
      return;
    }
    Player player = (Player) e.getEntity();
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null) {
      return;
    }
    e.setCancelled(true);

    User user = plugin.getUserManager().getUser(player);
    if (user.isSpectator() || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
  }

  @EventHandler
  public void onArrowDamage(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Arrow)) {
      return;
    }
    if (!(((Arrow) e.getDamager()).getShooter() instanceof Player)) {
      return;
    }
    Player attacker = (Player) ((Arrow) e.getDamager()).getShooter();
    if (!(e.getEntity() instanceof Player)) {
      return;
    }
    Player victim = (Player) e.getEntity();
    if (!ArenaUtils.areInSameArena(attacker, victim)) {
      return;
    }
    //we won't allow to suicide
    if (attacker == victim) {
      e.setCancelled(true);
      return;
    }
    Arena arena = ArenaRegistry.getArena(attacker);
    if (plugin.getUserManager().getUser(attacker).isSpectator()) {
      e.setCancelled(true);
      return;
    }
    if (arena.isTeammate(attacker, victim)) {
      e.setCancelled(true);
      return;
    }
    arena.addHits(victim, attacker);

    XSound.ENTITY_PLAYER_DEATH.play(victim.getLocation(), 50, 1);

    if (victim.getHealth() - e.getDamage() < 0) {
      return;
    }
    DecimalFormat df = new DecimalFormat("##.##");
    attacker.sendMessage(plugin.getChatManager().colorMessage("In-Game.Bow-Damage-Format").replace("%victim%", victim.getName()).replace("%hearts%", df.format(victim.getHealth() - e.getDamage())));
  }


  //todo fast die -> just teleporting the player instead of death and respawn
  @EventHandler(priority = EventPriority.HIGH)
  public void onPlayerDie(PlayerDeathEvent e) {
    Arena arena = ArenaRegistry.getArena(e.getEntity());
    if (arena == null) {
      return;
    }
    e.setDeathMessage("");
    e.getDrops().clear();
    e.setDroppedExp(0);
    // plugin.getCorpseHandler().spawnCorpse(e.getEntity(), arena);
    e.getEntity().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 7 * 20, 0));
    Player player = e.getEntity();
    if (arena.getArenaState() == ArenaState.STARTING) {
      return;
    } else if (arena.getArenaState() == ArenaState.ENDING || arena.getArenaState() == ArenaState.RESTARTING) {
      player.getInventory().clear();
      player.setFlying(false);
      player.setAllowFlight(false);
      return;
    }
    if (arena.getBase(player) == null) {
      return;
    }
    //if mode hearts and they are out it should set spec mode for them
    if (arena.getMode() == Arena.Mode.HEARTS && arena.getBase(player).getPoints() >= arena.getOption(ArenaOption.MODE_VALUE)) {
      User user = plugin.getUserManager().getUser(player);
      user.setSpectator(true);
      ArenaUtils.hidePlayer(player, arena);
      player.getInventory().clear();
      if (arena.getArenaState() != ArenaState.ENDING && arena.getArenaState() != ArenaState.RESTARTING) {
        arena.addDeathPlayer(player);
      }
      List<Player> players = arena.getBase(player).getPlayers();
      if (players.stream().allMatch(arena::isDeathPlayer)) {
        arena.addOut();
      }
    } else {
      player.setGameMode(GameMode.SURVIVAL);
      ArenaUtils.hidePlayersOutsideTheGame(player, arena);
      player.getInventory().clear();
    }
    //we must call it ticks later due to instant respawn bug
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
      e.getEntity().spigot().respawn();
    }, 5);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onRespawn(PlayerRespawnEvent e) {
    Player player = e.getPlayer();
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null) {
      return;
    }
    if (arena.getArenaState() == ArenaState.STARTING || arena.getArenaState() == ArenaState.WAITING_FOR_PLAYERS) {
      e.setRespawnLocation(arena.getLobbyLocation());
      return;
    } else if (arena.getArenaState() == ArenaState.ENDING || arena.getArenaState() == ArenaState.RESTARTING) {
      e.setRespawnLocation(arena.getSpectatorLocation());
      return;
    }
    if (arena.getPlayers().contains(player)) {
      User user = plugin.getUserManager().getUser(player);
      if (arena.inBase(player) && !user.isSpectator()) {
        cooldownOutside.put(player, System.currentTimeMillis());
        if (e.getPlayer().getLastDamageCause() != null && e.getPlayer().getLastDamageCause().getCause() != EntityDamageEvent.DamageCause.VOID) {
          player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3 * 20, 0));
        }
        e.setRespawnLocation(arena.getBase(player).getPlayerRespawnPoint());
        player.setAllowFlight(false);
        player.setFlying(false);
        ArenaUtils.hidePlayersOutsideTheGame(player, arena);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        plugin.getRewardsHandler().performReward(player, Reward.RewardType.DEATH);
        //Restock or give KitItems makes no difference? if using restock we need to save inventory as items are lost on dead
        //todo Maybe change it after fast dead is implemented (teleporting instead of dying)
        plugin.getUserManager().getUser(player).getKit().giveKitItems(player);
        if (!arena.getHits().containsKey(player)) {
          chatManager.broadcastAction(arena, player, ChatManager.ActionType.DEATH);
        }
        plugin.getUserManager().addStat(player, StatsStorage.StatisticType.DEATHS);
        user.addStat(StatsStorage.StatisticType.LOCAL_DEATHS, 1);
        rewardLastAttacker(arena, player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
          if (player != null) {
            if (!arena.getBase(player).getPortalCuboid().isInWithMarge(player.getLocation(), 5)) {
              player.teleport(arena.getBase(player).getPlayerRespawnPoint());
              player.getInventory().clear();
              plugin.getUserManager().getUser(player).getKit().giveKitItems(player);
              player.updateInventory();
            }
          }
        }, 5 /* 1/4 of a second as cooldown to prevent respawn from other plugins */);
      } else {
        e.setRespawnLocation(arena.getSpectatorLocation());
        player.setAllowFlight(true);
        player.setFlying(true);
        user.setSpectator(true);
        ArenaUtils.hidePlayer(player, arena);
        VersionUtils.setCollidable(player, false);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        plugin.getRewardsHandler().performReward(player, Reward.RewardType.DEATH);
        for (SpecialItem item : plugin.getSpecialItemManager().getSpecialItems()) {
          if (item.getDisplayStage() != SpecialItem.DisplayStage.SPECTATOR) {
            continue;
          }
          player.getInventory().setItem(item.getSlot(), item.getItemStack());
        }
      }
    }
  }

  @EventHandler
  public void onItemMove(InventoryClickEvent e) {
    if (e.getWhoClicked() instanceof Player && ArenaRegistry.isInArena((Player) e.getWhoClicked())) {
      if (ArenaRegistry.getArena(((Player) e.getWhoClicked())).getArenaState() != ArenaState.IN_GAME) {
        if (e.getClickedInventory() == e.getWhoClicked().getInventory()) {
          if (e.getView().getType() == InventoryType.CRAFTING || e.getView().getType() == InventoryType.PLAYER) {
            e.setResult(Event.Result.DENY);
          }
        }
      }
    }
  }

  @EventHandler
  public void playerCommandExecution(PlayerCommandPreprocessEvent e) {
    if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.ENABLE_SHORT_COMMANDS)) {
      Player player = e.getPlayer();
      if (e.getMessage().equalsIgnoreCase("/start")) {
        player.performCommand("tba forcestart");
        e.setCancelled(true);
        return;
      }
      if (e.getMessage().equalsIgnoreCase("/leave")) {
        player.performCommand("tb leave");
        e.setCancelled(true);
      }
    }
  }
}
