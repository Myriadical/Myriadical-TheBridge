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

package plugily.projects.thebridge.kits.level;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;
import plugily.projects.commonsbox.minecraft.compat.VersionUtils;
import plugily.projects.commonsbox.minecraft.compat.xseries.XMaterial;
import plugily.projects.commonsbox.minecraft.helper.ArmorHelper;
import plugily.projects.commonsbox.minecraft.helper.WeaponHelper;
import plugily.projects.commonsbox.minecraft.misc.ColorUtil;
import plugily.projects.thebridge.api.StatsStorage;
import plugily.projects.thebridge.arena.Arena;
import plugily.projects.thebridge.arena.ArenaRegistry;
import plugily.projects.thebridge.arena.ArenaState;
import plugily.projects.thebridge.kits.KitRegistry;
import plugily.projects.thebridge.kits.basekits.LevelKit;
import plugily.projects.thebridge.utils.Utils;

import java.util.List;

/**
 * Created by Tom on 28/07/2015.
 */
public class HardcoreKit extends LevelKit {

  public HardcoreKit() {
    setName(getPlugin().getChatManager().colorMessage("Kits.Hardcore.Name"));
    List<String> description = Utils.splitString(getPlugin().getChatManager().colorMessage("Kits.Hardcore.Description"), 40);
    this.setDescription(description.toArray(new String[0]));
    setLevel(getKitsConfig().getInt("Required-Level.Hardcore"));
    KitRegistry.registerKit(this);
  }

  @Override
  public boolean isUnlockedByPlayer(Player player) {
    return getPlugin().getUserManager().getUser(player).getStat(StatsStorage.StatisticType.LEVEL) >= this.getLevel() || player.hasPermission("thebridge.kit.hardcore");
  }

  @Override
  public void giveKitItems(Player player) {
    player.getInventory().addItem(WeaponHelper.getUnBreakingSword(WeaponHelper.ResourceType.WOOD, 10));
    player.getInventory().addItem(WeaponHelper.getEnchanted(XMaterial.DIAMOND_PICKAXE.parseItem(), new Enchantment[]{
      Enchantment.DURABILITY, Enchantment.DIG_SPEED}, new int[]{10, 2}));
    player.getInventory().addItem(Utils.getPotion(PotionType.INSTANT_HEAL, 2, true));
    player.getInventory().addItem(new ItemStack(Material.COOKIE, 10));
    VersionUtils.setMaxHealth(player, 10.0);
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    ArmorHelper.setColouredArmor(ColorUtil.fromChatColor(ChatColor.valueOf(arena.getBase(player).getColor().toUpperCase())), player);
    addBuildBlocks(player, arena);
  }

  @Override
  public Material getMaterial() {
    return XMaterial.PLAYER_HEAD.parseMaterial();
  }

  @Override
  public void reStock(Player player) {
    player.getInventory().addItem(Utils.getPotion(PotionType.INSTANT_HEAL, 2, true));
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    addBuildBlocks(player, arena);
  }
}
