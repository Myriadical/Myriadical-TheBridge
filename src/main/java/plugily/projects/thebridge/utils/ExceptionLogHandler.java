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

package plugily.projects.thebridge.utils;

import org.bukkit.Bukkit;
import plugily.projects.thebridge.Main;
import plugily.projects.thebridge.utils.services.exception.ReportedException;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * @author Tigerpanzer_02, 2Wild4You
 * <p>
 * Created at 31.10.2020
 */
public class ExceptionLogHandler extends Handler {

  //these classes if found in stacktraces won't be reported
  //to the Error Service
  private final List<String> blacklistedClasses = Arrays.asList("plugily.projects.thebridge.user.data.MysqlManager", "plugily.projects.thebridge.plajerlair.commonsbox.database.MysqlDatabase");
  private final Main plugin;

  public ExceptionLogHandler(Main plugin) {
    this.plugin = plugin;
    Bukkit.getLogger().addHandler(this);
  }

  @Override
  public void close() throws SecurityException {
  }

  @Override
  public void flush() {
  }

  @Override
  public void publish(LogRecord record) {
    Throwable throwable = record.getThrown();
    if (!(throwable instanceof Exception) || !throwable.getClass().getSimpleName().contains("Exception")) {
      return;
    }
    /*if (throwable.getStackTrace().length == 0
        || throwable.getCause() != null ? !throwable.getCause().getStackTrace()[0].getClassName().contains("pl.plajer.TheBridge")
        : !throwable.getStackTrace()[0].getClassName().contains("pl.plajer.TheBridge")) {
      return;
    }*/
    if (throwable.getStackTrace().length <= 0 || (throwable.getCause() != null &&
      !throwable.getCause().getStackTrace()[0].getClassName().contains("plugily.projects.thebridge"))) {
      return;
    }
    if (!throwable.getStackTrace()[0].getClassName().contains("plugily.projects.thebridge") || containsBlacklistedClass(throwable)) {
      return;
    }
    new ReportedException(plugin, (Exception) throwable);
    record.setThrown(null);
    record.setMessage("[The Bridge] We have found a bug in the code. Contact us at our official discord server (Invite link: https://discordapp.com/invite/UXzUdTP) with the following error given" +
      " above!");
  }

  private boolean containsBlacklistedClass(Throwable throwable) {
    for (StackTraceElement element : throwable.getStackTrace()) {
      for (String blacklist : blacklistedClasses) {
        if (element.getClassName().contains(blacklist)) {
          return true;
        }
      }
    }
    return false;
  }

}
