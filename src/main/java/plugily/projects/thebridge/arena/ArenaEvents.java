package plugily.projects.thebridge.arena;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.spigotmc.event.entity.EntityDismountEvent;
import pl.plajerlair.commonsbox.minecraft.compat.XMaterial;
import pl.plajerlair.commonsbox.minecraft.item.ItemBuilder;
import plugily.projects.thebridge.ConfigPreferences;
import plugily.projects.thebridge.Main;
import plugily.projects.thebridge.api.StatsStorage;
import plugily.projects.thebridge.arena.base.Base;
import plugily.projects.thebridge.arena.options.ArenaOption;
import plugily.projects.thebridge.handlers.ChatManager;
import plugily.projects.thebridge.handlers.items.SpecialItem;
import plugily.projects.thebridge.handlers.items.SpecialItemManager;
import plugily.projects.thebridge.handlers.rewards.Reward;
import plugily.projects.thebridge.user.User;
import plugily.projects.thebridge.utils.NMS;
import plugily.projects.thebridge.utils.Utils;

import java.util.HashMap;

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
    if (!canBuild(arena, player)) {
      event.setCancelled(true);
      return;
    }
    if (arena.getPlacedBlocks().contains(event.getBlock())){
    arena.removePlacedBlock(event.getBlock());
    } else {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onBuild(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    if (!canBuild(arena, player)) {
      event.setCancelled(true);
      return;
    }
    arena.addPlacedBlock(event.getBlock());
  }

  public boolean canBuild(Arena arena, Player player) {
    for (Base base : arena.getBases()) {
      if (base.getBaseCuboid().isIn(player)) {
        player.sendMessage("CANNOT BUILD/BREAK INSIDE BASE");
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
      plugin.getUserManager().getUser(attacker).addStat(StatsStorage.StatisticType.KILLS, 1);
      plugin.getUserManager().getUser(attacker).addStat(StatsStorage.StatisticType.LOCAL_KILLS, 1);
      attacker.sendMessage("You killed victim");
      victim.sendMessage("You were killed by attacker");
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
      if (arena.isTeammate(victim, attack)) {
        e.setCancelled(true);
      }
      arena.addHits(victim, attack);
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
        //kill the player and move to the spawn point
        victim.damage(1000.0);
        victim.teleport(arena.getBase(victim).getPlayerRespawnPoint());
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
    if (user.getCooldown("bow_shot") == 0) {
      user.setCooldown("bow_shot", plugin.getConfig().getInt("Bow-Cooldown", 5));
      Player player = (Player) e.getEntity();
      Utils.applyActionBarCooldown(player, plugin.getConfig().getInt("Bow-Cooldown", 5));
      NMS.setDurability(e.getBow(), (short) 0);
    } else {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onArrowPickup(PlayerPickupArrowEvent e) {
    if (ArenaRegistry.isInArena(e.getPlayer())) {
      e.getItem().remove();
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onItemPickup(EntityPickupItemEvent e) {
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
    if (ArenaRegistry.isInArena(attacker)) {
      e.setCancelled(true);
      e.getDamager().remove();
    }
    if (!(e.getEntity() instanceof Player)) {
      return;
    }
    Player victim = (Player) e.getEntity();
    if (!ArenaUtils.areInSameArena(attacker, victim)) {
      return;
    }
    //we won't allow to suicide
    if (attacker.equals(victim)) {
      e.setCancelled(true);
      return;
    }
    //todo do not kill player from same team
    Arena arena = ArenaRegistry.getArena(attacker);

    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_DEATH, 50, 1);
    victim.damage(100.0);

    User user = plugin.getUserManager().getUser(attacker);

    user.addStat(StatsStorage.StatisticType.KILLS, 1);
    user.addStat(StatsStorage.StatisticType.LOCAL_KILLS, 1);


    victim.sendTitle(chatManager.colorMessage("In-Game.Messages.Game-End-Messages.Titles.Died", victim), null, 5, 40, 50);
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
    e.getEntity().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 3 * 20, 0));
    Player player = e.getEntity();
    if (arena.getArenaState() == ArenaState.STARTING) {
      return;
    } else if (arena.getArenaState() == ArenaState.ENDING || arena.getArenaState() == ArenaState.RESTARTING) {
      player.getInventory().clear();
      player.setFlying(false);
      player.setAllowFlight(false);
      return;
    }
    rewardLastAttacker(arena, player);
    //if mode hearts and they are out it should set spec mode for them
    if (arena.getMode() == Arena.Mode.HEARTS && arena.getBase(player).getPoints() >= arena.getOption(ArenaOption.MODE_VALUE)) {
      User user = plugin.getUserManager().getUser(player);
      user.addStat(StatsStorage.StatisticType.DEATHS, 1);
      user.setSpectator(true);
      player.setCollidable(false);
      player.setGameMode(GameMode.SURVIVAL);
      ArenaUtils.hidePlayer(player, arena);
      player.setAllowFlight(true);
      player.setFlying(true);
      player.getInventory().clear();
      chatManager.broadcastAction(arena, player, ChatManager.ActionType.DEATH);
      if (arena.getArenaState() != ArenaState.ENDING && arena.getArenaState() != ArenaState.RESTARTING) {
        arena.addDeathPlayer(player);
      }
      //we must call it ticks later due to instant respawn bug
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        e.getEntity().spigot().respawn();
        for (SpecialItem item : plugin.getSpecialItemManager().getSpecialItems()) {
          if (item.getDisplayStage() != SpecialItem.DisplayStage.SPECTATOR) {
            continue;
          }
          player.getInventory().setItem(item.getSlot(), item.getItemStack());
        }
      }, 5);
    } else {
      User user = plugin.getUserManager().getUser(player);
      user.addStat(StatsStorage.StatisticType.DEATHS, 1);
      user.addStat(StatsStorage.StatisticType.LOCAL_DEATHS, 1);
      player.setGameMode(GameMode.SURVIVAL);
      ArenaUtils.hidePlayersOutsideTheGame(player, arena);
      player.getInventory().clear();
      chatManager.broadcastAction(arena, player, ChatManager.ActionType.DEATH);
      //we must call it ticks later due to instant respawn bug
      Bukkit.getScheduler().runTaskLater(plugin, () -> {
        e.getEntity().spigot().respawn();
        //todo give kit to player
      }, 5);
    }
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
      if (arena.inBase(player)) {
        e.setRespawnLocation(arena.getBase(player).getPlayerRespawnPoint());
        player.setAllowFlight(false);
        player.setFlying(false);
        ArenaUtils.hidePlayersOutsideTheGame(player, arena);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        plugin.getRewardsHandler().performReward(player, Reward.RewardType.DEATH);
      } else {
        e.setRespawnLocation(arena.getSpectatorLocation());
        player.setAllowFlight(true);
        player.setFlying(true);
        user.setSpectator(true);
        ArenaUtils.hidePlayer(player, arena);
        player.setCollidable(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        plugin.getRewardsHandler().performReward(player, Reward.RewardType.DEATH);
      }
    }
  }

  @EventHandler
  public void onItemMove(InventoryClickEvent e) {
    if (e.getWhoClicked() instanceof Player && ArenaRegistry.isInArena((Player) e.getWhoClicked())) {
      e.setResult(Event.Result.DENY);
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
