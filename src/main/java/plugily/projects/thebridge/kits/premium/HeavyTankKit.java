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

package plugily.projects.thebridge.kits.premium;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import plugily.projects.commonsbox.minecraft.compat.VersionUtils;
import plugily.projects.commonsbox.minecraft.compat.xseries.XMaterial;
import plugily.projects.commonsbox.minecraft.helper.ArmorHelper;
import plugily.projects.commonsbox.minecraft.helper.WeaponHelper;
import plugily.projects.thebridge.arena.Arena;
import plugily.projects.thebridge.arena.ArenaRegistry;
import plugily.projects.thebridge.arena.ArenaState;
import plugily.projects.thebridge.handlers.PermissionsManager;
import plugily.projects.thebridge.kits.KitRegistry;
import plugily.projects.thebridge.kits.basekits.PremiumKit;
import plugily.projects.thebridge.utils.Utils;

import java.util.List;

/**
 * Created by Tigerpanzer_02 on 27.12.2020.
 */
public class HeavyTankKit extends PremiumKit {

  public HeavyTankKit() {
    setName(getPlugin().getChatManager().colorMessage("Kits.Heavy-Tank.Name"));
    List<String> description = Utils.splitString(getPlugin().getChatManager().colorMessage("Kits.Heavy-Tank.Description"), 40);
    this.setDescription(description.toArray(new String[0]));
    KitRegistry.registerKit(this);
  }

  @Override
  public boolean isUnlockedByPlayer(Player player) {
    return player.hasPermission("thebridge.kit.heavytankkit") || PermissionsManager.gotKitsPerm(player);
  }

  @Override
  public void giveKitItems(Player player) {
    player.getInventory().addItem(WeaponHelper.getEnchanted(new ItemStack(Material.STICK), new Enchantment[]{Enchantment.DURABILITY, Enchantment.DAMAGE_ALL}, new int[]{3, 2}));
    player.getInventory().addItem(new ItemStack(XMaterial.COOKED_PORKCHOP.parseMaterial(), 8));
    VersionUtils.setMaxHealth(player, 40.0);
    player.getInventory().addItem(WeaponHelper.getEnchanted(XMaterial.DIAMOND_PICKAXE.parseItem(), new Enchantment[]{
      Enchantment.DURABILITY, Enchantment.DIG_SPEED}, new int[]{10, 2}));
    player.setHealth(40.0);
    ArmorHelper.setArmor(player, ArmorHelper.ArmorType.IRON);
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    addBuildBlocks(player, arena);

  }

  @Override
  public Material getMaterial() {
    return Material.DIAMOND_CHESTPLATE;
  }

  @Override
  public void reStock(Player player) {
    Arena arena = ArenaRegistry.getArena(player);
    if (arena == null || arena.getArenaState() != ArenaState.IN_GAME) {
      return;
    }
    addBuildBlocks(player, arena);
  }
}
