package com.fx.srp.utils;

import com.fx.srp.SpeedRunPlus;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class PlayerUtil {

    public static void resetPlayerState(SpeedRunPlus plugin, Player player) {
        UUID uuid = player.getUniqueId();

        // Reset the player's scoreboard
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        // Save the player's inventory and armor
        PlayerInventory inv = player.getInventory();
        plugin.getSavedInventories().put(uuid, inv.getContents().clone());
        plugin.getSavedArmor().put(uuid, inv.getArmorContents().clone());

        // Save player's advancements
        Map<Advancement, Set<String>> advancements = new HashMap<>();
        Bukkit.getServer().advancementIterator().forEachRemaining(advancement -> {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (!progress.getAwardedCriteria().isEmpty()) {
                advancements.put(advancement, new HashSet<>(progress.getAwardedCriteria()));
            }
        });
        plugin.getSavedAdvancements().put(uuid, advancements);

        // Reset stats
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setSaturation(20.0f);

        // Clear inventory and armor
        inv.clear();
        inv.setArmorContents(null);

        // Reset potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Reset advancements
        Bukkit.getServer().advancementIterator().forEachRemaining(advancement -> {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criteria : progress.getAwardedCriteria()) {
                progress.revokeCriteria(criteria);
            }
        });
    }

    public static void restorePlayerState(SpeedRunPlus plugin, Player player) {
        UUID uuid = player.getUniqueId();
        PlayerInventory inv = player.getInventory();

        // Reset the player's scoreboard
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());

        // Clean up references
        ItemStack[] savedInv = plugin.getSavedInventories().remove(uuid);
        ItemStack[] savedArmor = plugin.getSavedArmor().remove(uuid);
        Map<Advancement, Set<String>> savedAdvancements = plugin.getSavedAdvancements().remove(uuid);

        // Restore inventory and armor
        inv.clear();
        inv.setArmorContents(null);
        if (savedInv != null) inv.setContents(savedInv);
        if (savedArmor != null) inv.setArmorContents(savedArmor);

        // Restore advancements
        if (savedAdvancements == null) return;
        for (Map.Entry<Advancement, Set<String>> entry : savedAdvancements.entrySet()) {
            Advancement advancement = entry.getKey();
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            for (String criterion : entry.getValue()) {
                progress.awardCriteria(criterion);
            }
        }
    }

    public static void freezePlayer(SpeedRunPlus plugin, Player player) {
        plugin.getFrozenPlayers().add(player.getUniqueId());
        player.setWalkSpeed(0f);
        player.setFlySpeed(0f);
        player.setAllowFlight(false);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.JUMP,
                Integer.MAX_VALUE,
                128,
                false,
                false,
                false
        ));
    }

    public static void unfreezePlayer(SpeedRunPlus plugin, Player player) {
        plugin.getFrozenPlayers().remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.removePotionEffect(PotionEffectType.JUMP);
    }
}

