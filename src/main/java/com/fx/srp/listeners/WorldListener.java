package com.fx.srp.listeners;

import com.fx.srp.SpeedRunPlus;
import com.fx.srp.utils.PlayerUtil;
import com.fx.srp.utils.TimeFormatter;
import com.fx.srp.utils.WorldUtil;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.apache.commons.lang.time.StopWatch;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;
import java.util.logging.Logger;

public class WorldListener implements Listener {

    private final MVWorldManager worldManager;
    private final Logger logger = Bukkit.getLogger();

    SpeedRunPlus plugin = SpeedRunPlus.getPlugin(SpeedRunPlus.class);

    public WorldListener(MultiverseCore core) {
        this.worldManager = core.getMVWorldManager();
    }

    @EventHandler
    public void onDragonDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player player = entity.getKiller();

        if (entity.getType() == EntityType.ENDER_DRAGON && player != null) {
            UUID uuid = player.getUniqueId();

            // Get the final time
            StopWatch stopWatch = plugin.getPlayerStopWatches().get(uuid);
            stopWatch.stop();
            String formattedTime = new TimeFormatter(stopWatch).includeHours().superscriptMs().format();

            // Send some text
            player.sendTitle(
                    ChatColor.GREEN + "You win!",
                    ChatColor.GREEN + "Ender Dragon killed in: " +
                    ChatColor.ITALIC + ChatColor.GRAY + formattedTime,
                    10,
                    140,
                    20
            );
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.sendTitle(
                    ChatColor.DARK_RED + "Run Completed",
                    null,
                    10,
                    140,
                    20
            ), 250L);
            logger.info("[SRP] Player: " + player.getName() + ", completed a run in: " + formattedTime + "!");

            // Save the time
            plugin.leaderboardManager.finishRun(player, stopWatch.getTime());

            // Schedule world deletion & player restoral
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldUtil.deleteWorlds(plugin, worldManager, player);
                PlayerUtil.restorePlayerState(plugin, player);
            }, 400L);
        }
    }
}

