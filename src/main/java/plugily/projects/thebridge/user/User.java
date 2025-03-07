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

package plugily.projects.thebridge.user;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import plugily.projects.thebridge.Main;
import plugily.projects.thebridge.api.StatsStorage;
import plugily.projects.thebridge.api.events.player.TBPlayerStatisticChangeEvent;
import plugily.projects.thebridge.arena.Arena;
import plugily.projects.thebridge.arena.ArenaRegistry;
import plugily.projects.thebridge.kits.KitRegistry;
import plugily.projects.thebridge.kits.basekits.Kit;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Tigerpanzer_02, 2Wild4You
 * <p>
 * Created at 31.10.2020
 */
public class User {

  private static final Main plugin = JavaPlugin.getPlugin(Main.class);
  private static long cooldownCounter = 0;
  private final ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
  private final Map<StatsStorage.StatisticType, Integer> stats = new EnumMap<>(StatsStorage.StatisticType.class);
  private final Map<String, Double> cooldowns = new HashMap<>();
  private final Player player;
  private Kit kit = KitRegistry.getDefaultKit();
  private boolean spectator = false;
  private boolean permanentSpectator = false;
  public Scoreboard lastBoard;

  public User(Player player) {
    this.player = player;
  }

  public static void cooldownHandlerTask() {
    Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> cooldownCounter++, 20, 20);
  }

  public Arena getArena() {
    return ArenaRegistry.getArena(player);
  }

  public Player getPlayer() {
    return player;
  }

  public boolean isSpectator() {
    return spectator;
  }

  public void setSpectator(boolean spectator) {
    this.spectator = spectator;
  }

  public boolean isPermanentSpectator() {
    return permanentSpectator;
  }

  public void setPermanentSpectator(boolean permanentSpectator) {
    this.permanentSpectator = permanentSpectator;
  }

  public int getStat(StatsStorage.StatisticType stat) {
    if (!stats.containsKey(stat)) {
      stats.put(stat, 0);
      return 0;
    } else if (stats.get(stat) == null) {
      return 0;
    }
    return stats.get(stat);
  }

  public void removeScoreboard(Arena arena) {
    arena.getScoreboardManager().removeScoreboard(this);
    if (lastBoard != null) {
      getPlayer().setScoreboard(lastBoard);
      lastBoard = null;
    }
  }

  public void setStat(StatsStorage.StatisticType stat, int i) {
    stats.put(stat, i);

    //statistics manipulation events are called async when using mysql
    Bukkit.getScheduler().runTask(plugin, () -> {
      TBPlayerStatisticChangeEvent playerStatisticChangeEvent = new TBPlayerStatisticChangeEvent(getArena(), player, stat, i);
      Bukkit.getPluginManager().callEvent(playerStatisticChangeEvent);
    });
  }

  public void addStat(StatsStorage.StatisticType stat, int i) {
    stats.put(stat, getStat(stat) + i);

    //statistics manipulation events are called async when using mysql
    Bukkit.getScheduler().runTask(plugin, () -> {
      TBPlayerStatisticChangeEvent playerStatisticChangeEvent = new TBPlayerStatisticChangeEvent(getArena(), player, stat, getStat(stat));
      Bukkit.getPluginManager().callEvent(playerStatisticChangeEvent);
    });
  }

  public void setCooldown(String s, double seconds) {
    cooldowns.put(s, seconds + cooldownCounter);
  }

  public double getCooldown(String s) {
    return (!cooldowns.containsKey(s) || cooldowns.get(s) <= cooldownCounter) ? 0 : cooldowns.get(s) - cooldownCounter;
  }

  public Kit getKit() {
    return kit;
  }

  public void setKit(Kit kit) {
    this.kit = kit;
  }

}
