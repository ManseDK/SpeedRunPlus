package com.fx.srp.managers.gamemodes;

import com.fx.srp.SpeedRunPlus;
import com.fx.srp.managers.GameManager;
import com.fx.srp.managers.util.WorldManager;
import com.fx.srp.model.player.Speedrunner;
import com.fx.srp.model.requests.PendingRequest;
import com.fx.srp.model.run.Speedrun;
import com.fx.srp.model.run.CoopSpeedrun;
import com.fx.srp.model.run.TeamBattleSpeedrun;
import com.fx.srp.util.time.TimeFormatter;
import com.fx.srp.commands.GameMode;
import lombok.NonNull;
import org.apache.commons.lang.time.StopWatch;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Manager responsible for handling all aspects of the Coop game mode (cooperative speedruns).
 *
 * <p>Responsibilities include:</p>
 * <ul>
 *     <li>Tracking pending coop requests and enforcing timeouts</li>
 *     <li>Starting and stopping {@link CoopSpeedrun} instances</li>
 * </ul>
 */
public class CoopManager extends MultiplayerGameModeManager<CoopSpeedrun> {

    // Track coops that have accepted an invite but haven't had worlds created yet
    private final java.util.Map<java.util.UUID, CoopGroup> pendingCoops = new java.util.concurrent.ConcurrentHashMap<>();

    private static class CoopGroup {
        final Player leader;
        final Player partner;

        CoopGroup(Player leader, Player partner) {
            this.leader = leader;
            this.partner = partner;
        }
    }

    /**
     * Constructs a new CoopManager.
     */
    public CoopManager(SpeedRunPlus plugin, GameManager gameManager, WorldManager worldManager) {
        super(plugin, gameManager, worldManager);
    }

    /* ==========================================================
     *                       ACCEPT COOP
     * ========================================================== */
    /**
     * Accepts a pending coop request. Previously this created worlds immediately;
     * it now registers the accepted coop as a pending coop group and defers world
     * creation until a duel between two coops is accepted.
     */
    @Override
    public void start(Player partner) {
        // Pop the pending request so we can inspect its flags (teamInvite / duel)
        com.fx.srp.model.requests.PendingRequest request = popPendingRequest(partner);
        if (request == null) return;

        java.util.UUID senderUuid = request.getPlayerUUID();
        Player leader = org.bukkit.Bukkit.getPlayer(senderUuid);
        if (leader == null) {
            partner.sendMessage(ChatColor.YELLOW + "The other player is no longer available!");
            return;
        }

        // If this request was a duel request, both sides must have previously accepted a coop
        if (request.isDuel()) {
            // Both challenger and accepter must have pending coop groups
            CoopGroup challengerGroup = pendingCoops.get(leader.getUniqueId());
            CoopGroup accepterGroup = pendingCoops.get(partner.getUniqueId());

            if (challengerGroup == null) {
                leader.sendMessage(ChatColor.YELLOW + "Your coop is not ready to duel. Ensure you accepted your coop invite first.");
                partner.sendMessage(ChatColor.YELLOW + "The challenger is not ready for a duel.");
                return;
            }
            if (accepterGroup == null) {
                partner.sendMessage(ChatColor.YELLOW + "Your coop has not accepted an invite yet; cannot start a duel.");
                return;
            }

            // Build Speedrunner objects for all four players (2v2: leader+partner vs leader+partner)
            StopWatch stopWatch = new StopWatch();

            Speedrunner cLeaderRunner = new Speedrunner(challengerGroup.leader, stopWatch);
            Speedrunner cPartnerRunner = new Speedrunner(challengerGroup.partner, stopWatch);
            Speedrunner aLeaderRunner = new Speedrunner(accepterGroup.leader, stopWatch);
            Speedrunner aPartnerRunner = new Speedrunner(accepterGroup.partner, stopWatch);

            // Capture states
            cLeaderRunner.captureState();
            cPartnerRunner.captureState();
            aLeaderRunner.captureState();
            aPartnerRunner.captureState();

            // Create the TeamBattleSpeedrun
            TeamBattleSpeedrun teamRun = new TeamBattleSpeedrun(
                    GameMode.BATTLE,
                    cLeaderRunner,
                    cPartnerRunner,
                    aLeaderRunner,
                    aPartnerRunner,
                    stopWatch,
                    null
            );

            gameManager.registerRun(teamRun);
            // Initialize run (raw type dispatch to avoid generic mismatch)
            ((GameModeManager) this).initializeRun(teamRun);

            // Inform players
            challengerGroup.leader.sendMessage(ChatColor.YELLOW + "Creating the worlds for your teams...");
            challengerGroup.partner.sendMessage(ChatColor.YELLOW + "Creating the worlds for your teams...");
            accepterGroup.leader.sendMessage(ChatColor.YELLOW + "Creating the worlds for your teams...");
            accepterGroup.partner.sendMessage(ChatColor.YELLOW + "Creating the worlds for your teams...");

            // Create worlds for each team leader (each leader gets their own world set)
            worldManager.createWorldsForPlayers(java.util.List.of(challengerGroup.leader, accepterGroup.leader), null, (sets, seedType) -> {
                WorldManager.WorldSet challengerWorldSet = sets.get(challengerGroup.leader.getUniqueId());
                WorldManager.WorldSet accepterWorldSet = sets.get(accepterGroup.leader.getUniqueId());

                cLeaderRunner.setWorldSet(challengerWorldSet);
                cPartnerRunner.setWorldSet(challengerWorldSet);
                aLeaderRunner.setWorldSet(accepterWorldSet);
                aPartnerRunner.setWorldSet(accepterWorldSet);

                long seedLong = challengerWorldSet.getSpawn().getWorld().getSeed();
                teamRun.setSeed(seedLong);

                // Inform all players about the seed type
                String raw = seedType.name().toLowerCase().replace('_', ' ');
                String pretty = raw.substring(0, 1).toUpperCase() + raw.substring(1);
                challengerGroup.leader.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);
                challengerGroup.partner.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);
                accepterGroup.leader.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);
                accepterGroup.partner.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);

                // Freeze all
                cLeaderRunner.freeze();
                cPartnerRunner.freeze();
                aLeaderRunner.freeze();
                aPartnerRunner.freeze();

                // Teleport all
                challengerGroup.leader.teleport(cLeaderRunner.getWorldSet().getSpawn());
                challengerGroup.partner.teleport(cPartnerRunner.getWorldSet().getSpawn());
                accepterGroup.leader.teleport(aLeaderRunner.getWorldSet().getSpawn());
                accepterGroup.partner.teleport(aPartnerRunner.getWorldSet().getSpawn());

                // Reset states
                cLeaderRunner.resetState();
                cPartnerRunner.resetState();
                aLeaderRunner.resetState();
                aPartnerRunner.resetState();

                // Start countdown for the team battle run
                ((GameModeManager) this).startCountdown(teamRun, List.of(cLeaderRunner, cPartnerRunner, aLeaderRunner, aPartnerRunner));
            });

            // Remove pending coop entries now that worlds/runs are created
            pendingCoops.remove(challengerGroup.leader.getUniqueId());
            pendingCoops.remove(accepterGroup.leader.getUniqueId());

            return;
        }

        // FALLBACK: Normal coop acceptance -> register coop as pending group and do NOT create worlds
        // This defers world creation until someone challenges this coop to a duel.
        if (gameManager.isInRun(leader) || gameManager.isInRun(partner)) {
            partner.sendMessage(ChatColor.RED + "One of you is already in a speedrun!");
            return;
        }

        // Store the pending coop pairing
        pendingCoops.put(leader.getUniqueId(), new CoopGroup(leader, partner));

        leader.sendMessage(ChatColor.GREEN + partner.getName() + " has accepted your coop invite. Your coop is ready and waiting for a duel.");
        partner.sendMessage(ChatColor.GREEN + "You have joined " + leader.getName() + "'s coop. Awaiting a duel to create worlds and begin.");
        leader.sendMessage(ChatColor.YELLOW + "Use /srp coop duel <leader> to challenge another coop when you're ready.");
        partner.sendMessage(ChatColor.YELLOW + "Use /srp coop duel <leader> to challenge another coop when you're ready.");
    }

    /* ==========================================================
     *                       RESET COOP
     * ========================================================== */
    /**
     * Resets the worlds and state of a player in a {@link CoopSpeedrun}.
     *
     * <p>Does nothing.</p>
     *
     * @param player the player requesting the reset
     */
    @Override
    public void reset(Player player) {
        // Does nothing
    }

    /* ==========================================================
     *                       STOP COOP
     * ========================================================== */
    /**
     * Stops a {@link CoopSpeedrun}, showing results to both players.
     *
     * <p>Displays titles with winner information and formatted run time.
     * Calls {@link GameModeManager#finishRun} for cleanup.</p>
     *
     * @param winner any non-null Player declares all as winners
     */
    @Override
    public void stop(@NonNull Player winner) {
        // If not already in a speedrun
        Optional<Speedrun> optional = gameManager.getActiveRun(winner);
        if (optional.isEmpty()) {
            winner.sendMessage(ChatColor.RED + "You are not in a speedrun!");
            return;
        }

        // Get the run
        CoopSpeedrun coopSpeedrun = (CoopSpeedrun) optional.get();

        // Update the state
        coopSpeedrun.setState(Speedrun.State.FINISHED);

        // Get the players
        Player leader = coopSpeedrun.getLeader().getPlayer();
        Player partner = coopSpeedrun.getPartner().getPlayer();

        // Get the final time
        String formattedTime = new TimeFormatter(coopSpeedrun.getStopWatch())
                .withHours()
                .withSuperscriptMs()
                .format();

        // Announce winners
        leader.sendTitle(
                ChatColor.GREEN + "You won! ",
                ChatColor.GREEN + "With a time of: " +
                        ChatColor.ITALIC + ChatColor.GRAY + formattedTime,
                10,
                140,
                20
        );
        partner.sendTitle(
                ChatColor.GREEN + "You won! ",
                ChatColor.GREEN + "With a time of: " +
                        ChatColor.ITALIC + ChatColor.GRAY + formattedTime,
                10,
                140,
                20
        );

        finishRun(coopSpeedrun, 200);
    }

    // Method to send a coop request to another player
    public void sendCoopRequest(Player sender, Player target) {
        // Check if the target player is already in a coop (pending or active)
        if (isPlayerInCoop(target) || pendingCoops.containsKey(target.getUniqueId())) {
            sender.sendMessage(ChatColor.RED + "The player is already in a coop or pending coop.");
            return;
        }

        // Add logic to track the coop request
        // For now, just send a message to the target player
        target.sendMessage(ChatColor.GREEN + sender.getName() + " has invited you to join their coop. Type /srp coop accept to join.");
        sender.sendMessage(ChatColor.GREEN + "You have invited " + target.getName() + " to your coop.");
    }

    // Method to send a duel request to another coop leader
    public void sendDuelRequest(Player sender, Player targetLeader) {
        // Check if both players are coop leaders (must have pending coop groups)
        if (!isPlayerCoopLeader(sender)) {
            sender.sendMessage(ChatColor.RED + "You must be a coop leader (have an accepted coop) to send a duel request.");
            return;
        }

        if (!isPlayerCoopLeader(targetLeader)) {
            sender.sendMessage(ChatColor.RED + targetLeader.getName() + " is not a coop leader or their coop is not ready.");
            return;
        }

        // Create a duel pending request with timeout similar to MultiplayerGameModeManager.request
        java.util.UUID senderUUID = sender.getUniqueId();
        java.util.UUID targetUUID = targetLeader.getUniqueId();

        // target already has a pending request
        if (pendingRequests.containsKey(targetUUID)) {
            sender.sendMessage(
                    ChatColor.GRAY + targetLeader.getName() + " " +
                    ChatColor.YELLOW + " already has a pending request!"
            );
            return;
        }

        PendingRequest request = new PendingRequest(senderUUID, false, true);
        pendingRequests.put(targetUUID, request);

        sender.sendMessage(ChatColor.GREEN + "You have challenged " + targetLeader.getName() + "'s coop to a duel.");
        targetLeader.sendMessage(ChatColor.YELLOW + sender.getName() + " has challenged your coop to a duel! Type /srp coop accept to accept the challenge.");

        int taskId = org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingRequests.remove(targetUUID);
            sender.sendMessage(ChatColor.YELLOW + "Your duel request to " + ChatColor.GRAY + targetLeader.getName() + ChatColor.YELLOW + " has expired!");
            targetLeader.sendMessage(ChatColor.YELLOW + "The duel request has expired.");
        }, configHandler.getMaxRequestTime() / 50L).getTaskId();

        request.setTimeoutTaskId(taskId);
    }

    // Placeholder method to check if a player is in a coop (active run or pending)
    private boolean isPlayerInCoop(Player player) {
        // Check active runs
        if (gameManager.isInRun(player)) return true;
        // Check pending coop groups
        return pendingCoops.containsKey(player.getUniqueId()) || pendingCoops.values().stream().anyMatch(g -> g.partner.equals(player));
    }

    // Placeholder method to check if a player is a coop leader
    private boolean isPlayerCoopLeader(Player player) {
        // Leader is the player who created the pending coop group
        return pendingCoops.containsKey(player.getUniqueId());
    }
}
