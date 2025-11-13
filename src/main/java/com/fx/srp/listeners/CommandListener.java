package com.fx.srp.listeners;

import com.fx.srp.SpeedRunPlus;
import lombok.NoArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.logging.Logger;

@NoArgsConstructor
public class CommandListener implements Listener {

    private final Logger logger = Bukkit.getLogger();

    SpeedRunPlus plugin = SpeedRunPlus.getPlugin(SpeedRunPlus.class);

    // Prevent commands during a run
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Block commands if player is frozen (i.e. during countdown)
        if (plugin.getFrozenPlayers().contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You cannot use commands during the countdown!");
            return;
        }

        // Allow all commands if OP
        if (player.isOp()) return;

        // Only restrict if player is in a run world
        if (player.getWorld().getName().contains(player.getUniqueId().toString())) {
            String message = event.getMessage().toLowerCase();

            // Allow only /reset and /endrun
            if (message.equals("/reset") || message.equals("/endrun")) return;

            player.sendMessage(ChatColor.RED + "You cannot use commands during a run! Use "
                    + ChatColor.GRAY + "/endrun "
                    + ChatColor.RED + "to quit."
            );
            event.setCancelled(true);
        }
    }
}

