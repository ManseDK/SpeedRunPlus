package com.fx.srp.managers.gamemodes;

import com.fx.srp.SpeedRunPlus;
import com.fx.srp.managers.GameManager;
import com.fx.srp.managers.util.WorldManager;
import com.fx.srp.model.player.Speedrunner;
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

    /**
     * Constructs a new CoopManager.
     *
     * @param plugin the main {@link SpeedRunPlus} plugin instance
     * @param gameManager the {@link GameManager} for run registration and player management
     * @param worldManager the {@link WorldManager} for creating and deleting worlds
     */
    public CoopManager(SpeedRunPlus plugin, GameManager gameManager, WorldManager worldManager) {
        super(plugin, gameManager, worldManager);
    }

    /* ==========================================================
     *                       ACCEPT COOP
     * ========================================================== */
    /**
     * Accepts a pending coop request and starts a {@link CoopSpeedrun}.
     *
     * <p>Sets up a shared {@link StopWatch}, captures player states, and more.</p>
     *
     * @param partner the player accepting the request
     */
    @Override
    public void start(Player partner) {
        Player leader = getRequestSender(partner);
        if (leader == null) return;

        // Check if both players have selected available teammates -> start a 2v2 coop duel
        java.util.Optional<Player> leaderMateOpt = gameManager.getSelectedTeammate(leader);
        java.util.Optional<Player> partnerMateOpt = gameManager.getSelectedTeammate(partner);

        boolean leaderHasMate = leaderMateOpt.isPresent() && !gameManager.isInRun(leaderMateOpt.get()) && !leaderMateOpt.get().equals(partner);
        boolean partnerHasMate = partnerMateOpt.isPresent() && !gameManager.isInRun(partnerMateOpt.get()) && !partnerMateOpt.get().equals(leader);

        if (leaderHasMate && partnerHasMate) {
            // === START COOP-VS-COOP (2v2) using TeamBattleSpeedrun ===
            Player leaderMate = leaderMateOpt.get();
            Player partnerMate = partnerMateOpt.get();

            StopWatch stopWatch = new StopWatch();

            Speedrunner leaderRunner = new Speedrunner(leader, stopWatch);
            Speedrunner leaderMateRunner = new Speedrunner(leaderMate, stopWatch);
            Speedrunner partnerRunner = new Speedrunner(partner, stopWatch);
            Speedrunner partnerMateRunner = new Speedrunner(partnerMate, stopWatch);

            leaderRunner.captureState();
            leaderMateRunner.captureState();
            partnerRunner.captureState();
            partnerMateRunner.captureState();

            TeamBattleSpeedrun teamRun = new TeamBattleSpeedrun(
                    GameMode.BATTLE,
                    leaderRunner,
                    leaderMateRunner,
                    partnerRunner,
                    partnerMateRunner,
                    stopWatch,
                    null
            );

            gameManager.registerRun(teamRun);
            // TeamBattleSpeedrun is not a CoopSpeedrun; call generic initializer via raw type to avoid generic type mismatch
            ((GameModeManager) this).initializeRun(teamRun);

            leader.sendMessage(ChatColor.YELLOW + "Creating the worlds for your teams...");
            partner.sendMessage(ChatColor.YELLOW + "Creating the worlds for your teams...");

            // Create world-sets for both team leaders; teammates get same team world
            worldManager.createWorldsForPlayers(List.of(leader, partner), null, (sets, seedType) -> {
                WorldManager.WorldSet leaderWorldSet = sets.get(leader.getUniqueId());
                WorldManager.WorldSet partnerWorldSet = sets.get(partner.getUniqueId());

                leaderRunner.setWorldSet(leaderWorldSet);
                leaderMateRunner.setWorldSet(leaderWorldSet);
                partnerRunner.setWorldSet(partnerWorldSet);
                partnerMateRunner.setWorldSet(partnerWorldSet);

                long seedLong = leaderWorldSet.getSpawn().getWorld().getSeed();
                teamRun.setSeed(Long.valueOf(seedLong));

                // Inform all players about the seed type
                String raw = seedType.name().toLowerCase().replace('_', ' ');
                String pretty = raw.substring(0, 1).toUpperCase() + raw.substring(1);
                leader.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);
                leaderMate.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);
                partner.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);
                partnerMate.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);

                // Freeze all
                leaderRunner.freeze();
                leaderMateRunner.freeze();
                partnerRunner.freeze();
                partnerMateRunner.freeze();

                // Teleport all
                leader.teleport(leaderRunner.getWorldSet().getSpawn());
                leaderMate.teleport(leaderMateRunner.getWorldSet().getSpawn());
                partner.teleport(partnerRunner.getWorldSet().getSpawn());
                partnerMate.teleport(partnerMateRunner.getWorldSet().getSpawn());

                // Reset states
                leaderRunner.resetState();
                leaderMateRunner.resetState();
                partnerRunner.resetState();
                partnerMateRunner.resetState();

                // Use raw type dispatch to call startCountdown with a TeamBattleSpeedrun
                ((GameModeManager) this).startCountdown(teamRun, List.of(leaderRunner, leaderMateRunner, partnerRunner, partnerMateRunner));
            });

            return;
        }

        // FALLBACK: normal coop (2 players)
        StopWatch stopWatch = new StopWatch();
        Speedrunner leaderSpeedrunner = new Speedrunner(leader, stopWatch);
        Speedrunner partnerSpeedrunner = new Speedrunner(partner, stopWatch);

        // Capture the players' state - their inventory, levels, etc.
        leaderSpeedrunner.captureState();
        partnerSpeedrunner.captureState();

        CoopSpeedrun coopSpeedrun = new CoopSpeedrun(
                GameMode.COOP,
                leaderSpeedrunner,
                partnerSpeedrunner,
                stopWatch,
                null
        );
        gameManager.registerRun(coopSpeedrun);

        initializeRun(coopSpeedrun);

        leader.sendMessage(ChatColor.YELLOW + "Creating the world...");
        partner.sendMessage(ChatColor.YELLOW + "Creating the world...");
        worldManager.createWorldsForPlayers(List.of(leader), null, (sets, seedType) -> {
            // Get the set of worlds (overworld, nether, end) for each of the two players
            WorldManager.WorldSet leaderWorldSet = sets.get(leader.getUniqueId());

            // Assign them the world sets and set the shared seed
            leaderSpeedrunner.setWorldSet(leaderWorldSet);
            partnerSpeedrunner.setWorldSet(leaderWorldSet);
            coopSpeedrun.setSeed(leaderWorldSet.getOverworld().getSeed());

            // Inform both players about the seed type
            String raw = seedType.name().toLowerCase().replace('_', ' ');
            String pretty = raw.substring(0,1).toUpperCase() + raw.substring(1);
            leader.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);
            partner.sendMessage(ChatColor.AQUA + "Seed type: " + ChatColor.WHITE + pretty);

            // Freeze the players
            leaderSpeedrunner.freeze();
            partnerSpeedrunner.freeze();

            // Teleport players
            leader.teleport(leaderSpeedrunner.getWorldSet().getSpawn());
            partner.teleport(partnerSpeedrunner.getWorldSet().getSpawn());

            // Reset players' state (health, hunger, inventory, etc.)
            leaderSpeedrunner.resetState();
            partnerSpeedrunner.resetState();

            startCountdown(coopSpeedrun, List.of(leaderSpeedrunner, partnerSpeedrunner));
        });
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
}
