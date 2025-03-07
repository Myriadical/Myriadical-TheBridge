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

package plugily.projects.thebridge.handlers.setup;

import org.bukkit.entity.Player;
import plugily.projects.thebridge.arena.Arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BaseUtilities {

  private static final HashMap<Player, HashMap<String, Integer>> baseId = new HashMap<>();

  public static HashMap<Player, HashMap<String, Integer>> getBaseId() {
    return baseId;
  }

  private static final List<Player> editing = new ArrayList<>();

  public static boolean check(Arena arena, Player player) {
    if (!baseId.containsKey(player)) {
      return false;
    }
    return baseId.get(player).containsKey(arena.getId());
  }

  public static boolean isEditing(Player player) {
    return editing.contains(player);
  }

  public static void addEditing(Player player) {
    editing.remove(player);
    editing.add(player);
  }

  public static void removeEditing(Player player) {
    editing.remove(player);
  }
}
