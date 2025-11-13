package com.fx.srp.listeners;

import com.fx.srp.SpeedRunPlus;
import com.fx.srp.commands.Commands;
import com.fx.srp.commands.Subcommands;
import com.fx.srp.utils.PlayerUtil;
import com.fx.srp.utils.WorldUtil;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.logging.Logger;

public class PlayerListener implements Listener {

    private final MVWorldManager worldManager;
    private final Logger logger = Bukkit.getLogger();

    SpeedRunPlus plugin = SpeedRunPlus.getPlugin(SpeedRunPlus.class);

    public PlayerListener(MultiverseCore core) {
        this.worldManager = core.getMVWorldManager();

        // Start checking for AFK players
        startAfkChecker();
    }

    // Prevent frozen players from moving
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFrozenPlayers().contains(player.getUniqueId())) {
            if (event.getFrom().distanceSquared(event.getTo()) > 0) {
                event.setTo(event.getFrom());
            }
        }
    }

    // Prevent frozen players from interacting
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getFrozenPlayers().contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        World deathWorld = player.getWorld();
        UUID uuid = player.getUniqueId();

        // Only handle if they are in a speedrun world
        if (!deathWorld.getName().contains(uuid.toString())) return;

        // Get the spawn location and world where the player is about the respawn
        Location respawn = event.getRespawnLocation();
        World respawnWorld = respawn.getWorld();
        boolean isRespawnSpeedRunWorld = respawnWorld != null && respawnWorld.getName().contains(uuid.toString());

        // Ensure that, if respawning by anchor, it is in a speedrun world
        if (isRespawnSpeedRunWorld && event.isAnchorSpawn()) return;

        // Ensure that, if respawning in bed, it is in a speedrun world
        if (isRespawnSpeedRunWorld && event.isBedSpawn()) return;

        // Fallback to respawning in the speedrun overworld's spawn
        World overworld = Bukkit.getWorld(plugin.getSRP_OVERWORLD_PREFIX() + uuid);
        if (overworld != null) {
            respawn = overworld.getSpawnLocation();
            event.setRespawnLocation(respawn);
            return;
        }

        // Fallback to ending the run (something has gone wrong!)
        logger.warning(
                "[SRP] Failed to determine spawn-point of Player: " +
                player.getName() + ", died in World: " + deathWorld.getName()
        );
        player.performCommand(Commands.SRP.with(Subcommands.STOP));
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        World playerWorld = player.getWorld();

        boolean isInSpeedRunWorld = playerWorld.getName().contains(uuid.toString());
        boolean isInCountdown = plugin.getFrozenPlayers().contains(uuid);

        // Normal end run if not in countdown
        if (!isInCountdown && isInSpeedRunWorld) {
            player.performCommand(Commands.SRP.with(Subcommands.STOP));
        }

        // End run without stopping the timer (that does not exist - thereby causing a NP) if in countdown
        if (isInCountdown && isInSpeedRunWorld) {
            // Remove player tracking
            plugin.getFrozenPlayers().remove(uuid);
            plugin.getActivePlayers().remove(uuid);
            plugin.getPlayerLocations().remove(uuid);
            plugin.getSavedInventories().remove(uuid);
            plugin.getSavedArmor().remove(uuid);

            // Schedule world deletion
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldUtil.deleteWorlds(plugin, worldManager, player);
            }, 20L); // delay 1s to ensure cleanup runs safely

            // Teleport to main world and unfreeze them
            PlayerUtil.unfreezePlayer(plugin, player);
            player.teleport(plugin.getMainWorld().getSpawnLocation());
        }
    }

    // Identify and end the run of AFK players
    private void startAfkChecker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    if (!player.getWorld().getName().contains(uuid.toString())) continue;

                    // Get the last recorded location of the player and the time of their last activity
                    var lastLocation = plugin.getPlayerLocations().get(uuid);
                    var lastActivity = plugin.getActivePlayers().getOrDefault(uuid, now);

                    // If no previous location was recorded, set it and continue
                    if (lastLocation == null) {
                        plugin.getPlayerLocations().put(uuid, player.getLocation());
                        plugin.getActivePlayers().put(uuid, now);
                        continue;
                    }

                    // If the player changed worlds since the last check, record their location and continue
                    if (!player.getLocation().getWorld().equals(lastLocation.getWorld())) {
                        plugin.getPlayerLocations().put(uuid, player.getLocation());
                        plugin.getActivePlayers().put(uuid, now);
                        continue;
                    }

                    // Check how far they moved since last check
                    double distance = player.getLocation().distance(lastLocation);
                    if (distance >= plugin.getAFK_MIN_DISTANCE()) {
                        // If the player moved more than the minimum, refresh the time of their last activity
                        plugin.getPlayerLocations().put(uuid, player.getLocation());
                        plugin.getActivePlayers().put(uuid, now);
                        continue;
                    }

                    // Calculate the time the player has been AFK, warn them 1 minute before timeout
                    long timeAfk = now - lastActivity;
                    long warningTime = plugin.getAFK_TIMEOUT() - 60_000;
                    if (timeAfk >= warningTime && timeAfk < plugin.getAFK_TIMEOUT()) {
                        player.sendMessage(
                                ChatColor.YELLOW +
                                "You’ve been inactive for a while. Your run will end in 1 minute if AFK!"
                        );
                    }

                    // Check if AFK timeout exceeded
                    if (timeAfk >= plugin.getAFK_TIMEOUT()) {
                        player.sendMessage(ChatColor.RED + "You were AFK for too long. Your run has ended.");
                        player.performCommand(Commands.SRP.with(Subcommands.STOP));
                        plugin.getActivePlayers().remove(uuid);
                        plugin.getPlayerLocations().remove(uuid);
                    }
                }
            }
        }.runTaskTimer(plugin, plugin.getAFK_CHECK_INTERVAL(), plugin.getAFK_CHECK_INTERVAL());
    }
}

