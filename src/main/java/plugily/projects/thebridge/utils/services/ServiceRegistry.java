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

package plugily.projects.thebridge.utils.services;

import org.bukkit.plugin.java.JavaPlugin;
import plugily.projects.thebridge.utils.services.locale.LocaleService;
import plugily.projects.thebridge.utils.services.metrics.MetricsService;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;

/**
 * Class for registering new services
 */
public class ServiceRegistry {

  private static JavaPlugin registeredService;
  private static boolean serviceEnabled;
  private static long serviceCooldown = 0;
  private static LocaleService localeService;

  private ServiceRegistry() {
  }

  public static boolean registerService(JavaPlugin plugin) {
    if (registeredService != null && registeredService.equals(plugin)) {
      return false;
    }
    plugin.getLogger().log(Level.INFO, "Connecting to services, please wait! Server may freeze a bit!");
    try {
      HttpsURLConnection connection = (HttpsURLConnection) new URL("https://api.plugily.xyz/ping.php").openConnection();
      connection.setConnectTimeout(3000);
      connection.setReadTimeout(2000);
      connection.setRequestMethod("HEAD");
      connection.setRequestProperty("User-Agent", "PLService/1.0");
      int responseCode = connection.getResponseCode();
      if (responseCode != 200) {
        plugin.getLogger().log(Level.WARNING, "Plugily Projects services aren't online or inaccessible from your location! Response: " + responseCode + ". Do you think it's site problem? Contact developer! Make sure Cloudflare isn't blocked in your area!");
        serviceEnabled = false;
        return false;
      }
    } catch (IOException ignored) {
      plugin.getLogger().log(Level.WARNING, "Plugily Projects services aren't online or inaccessible from your location!");
      serviceEnabled = false;
      return false;
    }
    registeredService = plugin;
    serviceEnabled = true;
    plugin.getLogger().log(Level.INFO, "Hooked with ServiceRegistry! Initialized services properly!");
    new MetricsService(plugin);
    localeService = new LocaleService(plugin);
    return true;
  }

  public static JavaPlugin getRegisteredService() {
    return registeredService;
  }

  public static long getServiceCooldown() {
    return serviceCooldown;
  }

  public static void setServiceCooldown(long serviceCooldown) {
    ServiceRegistry.serviceCooldown = serviceCooldown;
  }

  public static LocaleService getLocaleService(JavaPlugin plugin) {
    return (!serviceEnabled || registeredService == null || !registeredService.equals(plugin)) ? null : localeService;
  }

  public static boolean isServiceEnabled() {
    return serviceEnabled;
  }
}