package plugily.projects.thebridge.arena;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.plajerlair.commonsbox.minecraft.configuration.ConfigUtils;
import pl.plajerlair.commonsbox.minecraft.serialization.InventorySerializer;
import plugily.projects.thebridge.ConfigPreferences;
import plugily.projects.thebridge.Main;
import plugily.projects.thebridge.api.StatsStorage;
import plugily.projects.thebridge.api.events.game.TBGameStartEvent;
import plugily.projects.thebridge.api.events.game.TBGameStateChangeEvent;
import plugily.projects.thebridge.arena.base.Base;
import plugily.projects.thebridge.arena.managers.ScoreboardManager;
import plugily.projects.thebridge.arena.options.ArenaOption;
import plugily.projects.thebridge.handlers.ChatManager;
import plugily.projects.thebridge.handlers.rewards.Reward;
import plugily.projects.thebridge.user.User;
import plugily.projects.thebridge.utils.Debugger;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import plugily.projects.thebridge.utils.NMS;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * @author Tigerpanzer_02 & 2Wild4You
 * <p>
 * Created at 31.10.2020
 */
public class Arena extends BukkitRunnable {

  private static final Main plugin = JavaPlugin.getPlugin(Main.class);
  private final String id;
  private ArenaState arenaState = ArenaState.WAITING_FOR_PLAYERS;
  private BossBar gameBar;
  private String mapName = "";
  private final ChatManager chatManager = plugin.getChatManager();
  private final ScoreboardManager scoreboardManager;
  private final Set<Player> players = new HashSet<>();
  private final List<Player> spectators = new ArrayList<>(), deaths = new ArrayList<>();
  //all arena values that are integers, contains constant and floating values
  private final Map<ArenaOption, Integer> arenaOptions = new EnumMap<>(ArenaOption.class);
  //instead of 3 location fields we use map with GameLocation enum
  private final Map<GameLocation, Location> gameLocations = new EnumMap<>(GameLocation.class);
  private boolean ready = true, forceStart = false;
  private List<Base> bases = new ArrayList<>();
  private Mode mode;


  public Arena(String id) {
    this.id = id;
    for (ArenaOption option : ArenaOption.values()) {
      arenaOptions.put(option, option.getDefaultValue());
    }
    if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
      gameBar = Bukkit.createBossBar(chatManager.colorMessage("Bossbar.Main-Title"), BarColor.BLUE, BarStyle.SOLID);
    }
    scoreboardManager = new ScoreboardManager(this);
  }

  public boolean isReady() {
    return ready;
  }

  public void setReady(boolean ready) {
    this.ready = ready;
  }

  @Override
  public void run() {
    //idle task
    if (getPlayers().isEmpty() && getArenaState() == ArenaState.WAITING_FOR_PLAYERS) {
      return;
    }
    Debugger.performance("ArenaTask", "[PerformanceMonitor] [{0}] Running game task", getId());
    long start = System.currentTimeMillis();

    switch (getArenaState()) {
      case WAITING_FOR_PLAYERS:
        if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
          plugin.getServer().setWhitelist(false);
        }
        if (getPlayers().size() < getMinimumPlayers()) {
          if (getTimer() <= 0) {
            setTimer(45);
            chatManager.broadcast(this, chatManager.formatMessage(this, chatManager.colorMessage("In-Game.Messages.Lobby-Messages.Waiting-For-Players"), getMinimumPlayers()));
            break;
          }
        } else {
          if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
            gameBar.setTitle(chatManager.colorMessage("Bossbar.Waiting-For-Players"));
          }
          chatManager.broadcast(this, chatManager.colorMessage("In-Game.Messages.Lobby-Messages.Enough-Players-To-Start"));
          setArenaState(ArenaState.STARTING);
          setTimer(plugin.getConfig().getInt("Starting-Waiting-Time", 60));
          this.showPlayers();
        }
        setTimer(getTimer() - 1);
        break;
      case STARTING:
        if (getPlayers().size() == getMaximumPlayers() && getTimer() >= plugin.getConfig().getInt("Start-Time-On-Full-Lobby", 15) && !forceStart) {
          setTimer(plugin.getConfig().getInt("Start-Time-On-Full-Lobby", 15));
          chatManager.broadcast(this, chatManager.colorMessage("In-Game.Messages.Lobby-Messages.Start-In").replace("%TIME%", String.valueOf(getTimer())));
        }
        if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
          gameBar.setTitle(chatManager.colorMessage("Bossbar.Starting-In").replace("%time%", String.valueOf(getTimer())));
          gameBar.setProgress(getTimer() / plugin.getConfig().getDouble("Starting-Waiting-Time", 60));
        }
        for (Player player : getPlayers()) {
          player.setExp((float) (getTimer() / plugin.getConfig().getDouble("Starting-Waiting-Time", 60)));
          player.setLevel(getTimer());
        }
        if (getPlayers().size() < getMinimumPlayers() && !forceStart) {
          if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
            gameBar.setTitle(chatManager.colorMessage("Bossbar.Waiting-For-Players"));
            gameBar.setProgress(1.0);
          }
          chatManager.broadcast(this, chatManager.formatMessage(this, chatManager.colorMessage("In-Game.Messages.Lobby-Messages.Waiting-For-Players"), getMinimumPlayers()));
          setArenaState(ArenaState.WAITING_FOR_PLAYERS);
          Bukkit.getPluginManager().callEvent(new TBGameStartEvent(this));
          setTimer(15);
          for (Player player : getPlayers()) {
            player.setExp(1);
            player.setLevel(0);
          }
          if (forceStart) {
            forceStart = false;
          }
          break;
        }
        if (getTimer() == 0 || forceStart) {
          TBGameStartEvent gameStartEvent = new TBGameStartEvent(this);
          Bukkit.getPluginManager().callEvent(gameStartEvent);
          setArenaState(ArenaState.IN_GAME);
          if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
            gameBar.setProgress(1.0);
          }
          setTimer(5);
          if (players.isEmpty()) {
            break;
          }
          teleportAllToStartLocation();
          for (Player player : getPlayers()) {
            //reset local variables to be 100% sure
            plugin.getUserManager().getUser(player).setStat(StatsStorage.StatisticType.LOCAL_DEATHS, 0);
            plugin.getUserManager().getUser(player).setStat(StatsStorage.StatisticType.LOCAL_KILLS, 0);
            //
            player.getInventory().clear();
            player.setGameMode(GameMode.SURVIVAL);
            ArenaUtils.hidePlayersOutsideTheGame(player, this);
            player.updateInventory();
            plugin.getUserManager().getUser(player).addStat(StatsStorage.StatisticType.GAMES_PLAYED, 1);
            setTimer(plugin.getConfig().getInt("Gameplay-Time", 500));
            player.sendMessage(chatManager.getPrefix() + chatManager.colorMessage("In-Game.Messages.Lobby-Messages.Game-Started"));
            if (!inBase(player)) {
              for (Base base : getBases()) {
                if (base.getPlayers().size() >= base.getMaximumSize()) continue;
                //todo try to redo teams if they would be unfair (one team got more players than other)
                base.addPlayer(player);
              }
            }
            plugin.getUserManager().getUser(player).getKit().giveKitItems(player);
            player.updateInventory();
          }
          teleportAllToBaseLocation();
          if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
            gameBar.setTitle(chatManager.colorMessage("Bossbar.In-Game-Info"));
          }
        }
        if (forceStart) {
          forceStart = false;
        }
        setTimer(getTimer() - 1);
        break;
      case IN_GAME:
        if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
          plugin.getServer().setWhitelist(getMaximumPlayers() <= getPlayers().size());
        }
        if (getTimer() <= 0) {
          ArenaManager.stopGame(false, this);
        }

        if (getTimer() == 30 || getTimer() == 60 || getTimer() == 120) {
          String title = chatManager.colorMessage("In-Game.Messages.Seconds-Left-Title").replace("%time%", String.valueOf(getTimer()));
          String subtitle = chatManager.colorMessage("In-Game.Messages.Seconds-Left-Subtitle").replace("%time%", String.valueOf(getTimer()));
          for (Player p : getPlayers()) {
            p.sendTitle(title, subtitle, 5, 40, 5);
          }
        }

        //no players - stop game
        if (getPlayersLeft().size() == 0) {
          ArenaManager.stopGame(false, this);
        } else {
          //winner check
          for (Base base : getBases()) {
            if (base.getPoints() >= getOption(ArenaOption.MODE_VALUE)){
              for (Player p : getPlayers()) {
                p.sendTitle(chatManager.colorMessage("In-Game.Messages.Game-End-Messages.Titles.Lose"),
                  chatManager.colorMessage("In-Game.Messages.Game-End-Messages.Subtitles.REACHED VALUE"), 5, 40, 5);
                if (base.getPlayers().contains(p)) {
                  p.sendTitle(chatManager.colorMessage("In-Game.Messages.Game-End-Messages.Titles.Win"), null, 5, 40, 5);
                }
              }
              ArenaManager.stopGame(false, this);
            }
          }
        }
        setTimer(getTimer() - 1);
        break;
      case ENDING:
        scoreboardManager.stopAllScoreboards();
        if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
          plugin.getServer().setWhitelist(false);
        }
        if (getTimer() <= 0) {
          if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
            gameBar.setTitle(chatManager.colorMessage("Bossbar.Game-Ended"));
          }

          List<Player> playersToQuit = new ArrayList<>(getPlayers());
          for (Player player : playersToQuit) {
            plugin.getUserManager().getUser(player).removeScoreboard();
            player.setGameMode(GameMode.SURVIVAL);
            for (Player players : Bukkit.getOnlinePlayers()) {
              NMS.showPlayer(player, players);
              if (!ArenaRegistry.isInArena(players)) {
                NMS.showPlayer(players, player);
              }
            }
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            player.setWalkSpeed(0.2f);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.getInventory().clear();

            player.getInventory().setArmorContents(null);
            doBarAction(BarAction.REMOVE, player);
            player.setFireTicks(0);
            player.setFoodLevel(20);
          }
          teleportAllToEndLocation();

          if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.INVENTORY_MANAGER_ENABLED)) {
            for (Player player : getPlayers()) {
              InventorySerializer.loadInventory(plugin, player);
            }
          }

          chatManager.broadcast(this, chatManager.colorMessage("Commands.Teleported-To-The-Lobby"));

          for (User user : plugin.getUserManager().getUsers(this)) {
            user.setSpectator(false);
            user.getPlayer().setCollidable(true);
            plugin.getUserManager().saveAllStatistic(user);
          }
          plugin.getRewardsHandler().performReward(this, Reward.RewardType.END_GAME);
          players.clear();

          deaths.clear();
          spectators.clear();

          cleanUpArena();
          if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)
            && ConfigUtils.getConfig(plugin, "bungee").getBoolean("Shutdown-When-Game-Ends")) {
            plugin.getServer().shutdown();
          }
          setArenaState(ArenaState.RESTARTING);
        }
        setTimer(getTimer() - 1);
        break;
      case RESTARTING:
        getPlayers().clear();
        for (Base base : getBases()){
          base.reset();
        }
        setArenaState(ArenaState.WAITING_FOR_PLAYERS);
        if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)) {
          ArenaRegistry.shuffleBungeeArena();
          for (Player player : Bukkit.getOnlinePlayers()) {
            ArenaManager.joinAttempt(player, ArenaRegistry.getArenas().get(ArenaRegistry.getBungeeArena()));
          }
        }
        if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
          gameBar.setTitle(chatManager.colorMessage("Bossbar.Waiting-For-Players"));
        }
        break;
      default:
        break;
    }
  }


  /**
   * Get arena identifier used to get arenas by string.
   *
   * @return arena name
   * @see ArenaRegistry#getArena(String)
   */
  public String getId() {
    return id;
  }

  /**
   * Get minimum players needed.
   *
   * @return minimum players needed to start arena
   */
  public int getMinimumPlayers() {
    return getOption(ArenaOption.MINIMUM_PLAYERS);
  }

  /**
   * Set minimum players needed.
   *
   * @param minimumPlayers players needed to start arena
   */
  public void setMinimumPlayers(int minimumPlayers) {
    if (minimumPlayers < 2) {
      Debugger.debug(Level.WARNING, "Minimum players amount for arena cannot be less than 2! Got {0}", minimumPlayers);
      setOptionValue(ArenaOption.MINIMUM_PLAYERS, 2);
      return;
    }
    setOptionValue(ArenaOption.MINIMUM_PLAYERS, minimumPlayers);
  }

  /**
   * Get arena map name.
   *
   * @return arena map name, it's not arena id
   * @see #getId()
   */
  @NotNull
  public String getMapName() {
    return mapName;
  }

  /**
   * Set arena map name.
   *
   * @param mapname new map name, it's not arena id
   */
  public void setMapName(@NotNull String mapname) {
    this.mapName = mapname;
  }

  /**
   * Get timer of arena.
   *
   * @return timer of lobby time / time to next wave
   */
  public int getTimer() {
    return getOption(ArenaOption.TIMER);
  }

  /**
   * Modify game timer.
   *
   * @param timer timer of lobby / time to next wave
   */
  public void setTimer(int timer) {
    setOptionValue(ArenaOption.TIMER, timer);
  }

  /**
   * Return maximum players arena can handle.
   *
   * @return maximum players arena can handle
   */
  public int getMaximumPlayers() {
    return getOption(ArenaOption.MAXIMUM_PLAYERS);
  }

  /**
   * Set maximum players arena can handle.
   *
   * @param maximumPlayers how many players arena can handle
   */
  public void setMaximumPlayers(int maximumPlayers) {
    setOptionValue(ArenaOption.MAXIMUM_PLAYERS, maximumPlayers);
  }

  public List<Base> getBases() {
    return bases;
  }

  public boolean inBase(Player player) {
    return getBases().stream().anyMatch(base -> base.getPlayers().contains(player));
  }

  /**
   * Returns base where the player is
   *
   * @param p target player
   * @return Base or null if not inside an base
   * @see #inBase(Player) to check if player is playing
   */
  public Base getBase(Player p) {
    if (p == null || !p.isOnline()) {
      return null;
    }
    for (Base base : getBases()) {
      for (Player player : base.getPlayers()) {
        if (player.getUniqueId().equals(p.getUniqueId())) {
          return base;
        }
      }
    }
    return null;
  }

  public void setBases(List<Base> bases) {
    this.bases = bases;
  }

  public void addBase(Base base) {
    this.bases.add(base);
  }

  public void removeBase(Base base) {
    this.bases.remove(base);
  }

  /**
   * Return game state of arena.
   *
   * @return game state of arena
   * @see ArenaState
   */
  @NotNull
  public ArenaState getArenaState() {
    return arenaState;
  }

  /**
   * Set game state of arena.
   *
   * @param arenaState new game state of arena
   * @see ArenaState
   */
  public void setArenaState(@NotNull ArenaState arenaState) {
    this.arenaState = arenaState;

    TBGameStateChangeEvent gameStateChangeEvent = new TBGameStateChangeEvent(this, getArenaState());
    Bukkit.getPluginManager().callEvent(gameStateChangeEvent);

    plugin.getSignManager().updateSigns();
  }

  /**
   * Get all players in arena.
   *
   * @return set of players in arena
   */
  @NotNull
  public Set<Player> getPlayers() {
    return players;
  }

  public void teleportToLobby(Player player) {
    player.setFoodLevel(20);
    player.setFlying(false);
    player.setAllowFlight(false);
    player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
    player.setWalkSpeed(0.2f);
    Location location = getLobbyLocation();
    if (location == null) {
      System.out.print("LobbyLocation isn't intialized for arena " + getId());
      return;
    }
    player.teleport(location);
  }

  public ScoreboardManager getScoreboardManager() {
    return scoreboardManager;
  }

  public void addDeathPlayer(Player player) {
    deaths.add(player);
  }

  public void removeDeathPlayer(Player player) {
    deaths.remove(player);
  }

  public boolean isDeathPlayer(Player player) {
    return deaths.contains(player);
  }

  public void addSpectatorPlayer(Player player) {
    spectators.add(player);
  }

  public void removeSpectatorPlayer(Player player) {
    spectators.remove(player);
  }

  public boolean isSpectatorPlayer(Player player) {
    return spectators.contains(player);
  }

  /**
   * Executes boss bar action for arena
   *
   * @param action add or remove a player from boss bar
   * @param p      player
   */
  public void doBarAction(BarAction action, Player p) {
    if (!plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BOSSBAR_ENABLED)) {
      return;
    }
    switch (action) {
      case ADD:
        gameBar.addPlayer(p);
        break;
      case REMOVE:
        gameBar.removePlayer(p);
        break;
      default:
        break;
    }
  }

  public void cleanUpArena() {
    //todo
  }


  /**
   * Get lobby location of arena.
   *
   * @return lobby location of arena
   */
  @Nullable
  public Location getLobbyLocation() {
    return gameLocations.get(GameLocation.LOBBY);
  }

  /**
   * Set lobby location of arena.
   *
   * @param loc new lobby location of arena
   */
  public void setLobbyLocation(Location loc) {
    gameLocations.put(GameLocation.LOBBY, loc);
  }

  public void teleportToStartLocation(Player player) {
    player.teleport(getMidLocation());
  }

  public void teleportToBaseLocation(Player player) {
    player.teleport(getBase(player).getPlayerSpawnPoint());
  }

  private void teleportAllToStartLocation() {
    for (Player player : players) {
      player.teleport(getMidLocation());
    }
  }

  private void teleportAllToBaseLocation() {
    for (Player player : players) {
      player.teleport(getBase(player).getPlayerSpawnPoint());
    }
  }

  public void setForceStart(boolean forceStart) {
    this.forceStart = forceStart;
  }


  public void teleportAllToEndLocation() {
    if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)
      && ConfigUtils.getConfig(plugin, "bungee").getBoolean("End-Location-Hub", true)) {
      getPlayers().forEach(plugin.getBungeeManager()::connectToHub);
      return;
    }

    Location location = getEndLocation();
    if (location == null) {
      location = getLobbyLocation();
      System.out.print("EndLocation for arena " + getId() + " isn't intialized!");
    }

    if (location != null) {
      for (Player player : getPlayers()) {
        player.teleport(location);
      }
    }
  }

  public void teleportToEndLocation(Player player) {
    if (plugin.getConfigPreferences().getOption(ConfigPreferences.Option.BUNGEE_ENABLED)
      && ConfigUtils.getConfig(plugin, "bungee").getBoolean("End-Location-Hub", true)) {
      plugin.getBungeeManager().connectToHub(player);
      return;
    }

    Location location = getEndLocation();
    if (location == null) {
      System.out.print("EndLocation for arena " + getId() + " isn't intialized!");
      location = getLobbyLocation();
    }

    if (location != null) {
      player.teleport(location);
    }
  }

  public void start() {
    Debugger.debug("[{0}] Game instance started", getId());
    this.runTaskTimer(plugin, 20L, 20L);
    this.setArenaState(ArenaState.RESTARTING);
  }

  void addPlayer(Player player) {
    players.add(player);
  }

  void removePlayer(Player player) {
    if (player != null) {
      players.remove(player);
    }
  }

  public List<Player> getPlayersLeft() {
    return plugin.getUserManager().getUsers(this).stream().filter(user -> !user.isSpectator()).map(User::getPlayer).collect(Collectors.toList());
  }

  void showPlayers() {
    for (Player player : getPlayers()) {
      for (Player p : getPlayers()) {
        NMS.showPlayer(player, p);
        NMS.showPlayer(p, player);
      }
    }
  }

  /**
   * Get end location of arena.
   *
   * @return end location of arena
   */
  @Nullable
  public Location getEndLocation() {
    return gameLocations.get(GameLocation.END);
  }

  /**
   * Set end location of arena.
   *
   * @param endLoc new end location of arena
   */
  public void setEndLocation(Location endLoc) {
    gameLocations.put(GameLocation.END, endLoc);
  }

  /**
   * Get mid location of arena.
   *
   * @return mid location of arena
   */
  @Nullable
  public Location getMidLocation() {
    return gameLocations.get(GameLocation.MID);
  }

  /**
   * Set mid location of arena.
   *
   * @param midLoc new end location of arena
   */
  public void setMidLocation(Location midLoc) {
    gameLocations.put(GameLocation.MID, midLoc);
  }

  /**
   * Get spectator location of arena.
   *
   * @return end location of arena
   */
  @Nullable
  public Location getSpectatorLocation() {
    return gameLocations.get(GameLocation.SPECTATOR);
  }

  /**
   * Set spectator location of arena.
   *
   * @param spectatorLoc new end location of arena
   */
  public void setSpectatorLocation(Location spectatorLoc) {
    gameLocations.put(GameLocation.END, spectatorLoc);
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public int getOption(@NotNull ArenaOption option) {
    return arenaOptions.get(option);
  }

  public void setOptionValue(ArenaOption option, int value) {
    arenaOptions.put(option, value);
  }

  public void addOptionValue(ArenaOption option, int value) {
    arenaOptions.put(option, arenaOptions.get(option) + value);
  }

  public enum BarAction {
    ADD, REMOVE
  }

  public enum GameLocation {
    LOBBY, END, SPECTATOR, MID
  }

  public enum Mode {
    HEARTS, POINTS
  }
}
