package com.fx.srp.managers.gamemodes;

import com.fx.srp.SpeedRunPlus;
import com.fx.srp.managers.GameManager;
import com.fx.srp.managers.util.WorldManager;
import com.fx.srp.model.run.Speedrun;
import com.fx.srp.model.player.Speedrunner;
import com.fx.srp.model.run.BattleSpeedrun;
import com.fx.srp.model.run.TeamBattleSpeedrun;
import com.fx.srp.util.time.TimeFormatter;
import com.fx.srp.commands.GameMode;
import lombok.NonNull;
import org.apache.commons.lang.time.StopWatch;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manager responsible for handling all aspects of the Battle game mode (1v1 speedrun battles).
 *
 * <p>Responsibilities include:</p>
 * <ul>
 *     <li>Tracking pending battle requests and enforcing timeouts</li>
 *     <li>Starting and stopping {@link BattleSpeedrun} instances</li>
 *     <li>Resetting player worlds and state</li>
 *     <li>Determining winners and results</li>
 * </ul>
 */
public class BattleManager extends MultiplayerGameModeManager<Speedrun> {

    /**
     * Constructs a new BattleManager.
     *
     * @param plugin the main {@link SpeedRunPlus} plugin instance
     * @param gameManager the {@link GameManager} for run registration and player management
     * @param worldManager the {@link WorldManager} for creating and deleting worlds
     */
    public BattleManager(SpeedRunPlus plugin, GameManager gameManager, WorldManager worldManager) {
        super(plugin, gameManager, worldManager);
    }

    /* ==========================================================
     *                       ACCEPT BATTLE
     * ========================================================== */
    /**
     * Accepts a pending battle request and starts a {@link BattleSpeedrun}.
     *
     * <p>Sets up a shared {@link StopWatch}, captures player states, and more.</p>
     *
     * @param challengee the player accepting the request
     */
    @Override
    public void start(Player challengee) {
        Player challenger = getRequestSender(challengee);
        if (challenger == null) return;

        // Check for 2v2 teammates
        java.util.Optional<Player> challengerMate = gameManager.getSelectedTeammate(challenger);
        java.util.Optional<Player> challengeeMate = gameManager.getSelectedTeammate(challengee);

        // Validate teammates are available and not in other runs
        boolean canStartTeam = challengerMate.isPresent() && challengeeMate.isPresent()
                && !gameManager.isInRun(challengerMate.get())
                && !gameManager.isInRun(challengeeMate.get())
                // ensure teammates are distinct and not equal to opponent
                && !challengerMate.get().equals(challengee)
                && !challengeeMate.get().equals(challenger)
                && !challenger.equals(challengee);

        if (canStartTeam) {
            // === START 2v2 ===
            Player challengerTeamMate = challengerMate.get();
            Player challengeeTeamMate = challengeeMate.get();

            StopWatch stopWatch = new StopWatch();

            Speedrunner challengerSpeedrunner = new Speedrunner(challenger, stopWatch);
            Speedrunner challengerMateRunner = new Speedrunner(challengerTeamMate, stopWatch);
            Speedrunner challengeeSpeedrunner = new Speedrunner(challengee, stopWatch);
            Speedrunner challengeeMateRunner = new Speedrunner(challengeeTeamMate, stopWatch);

            // Capture states
            challengerSpeedrunner.captureState();
            challengerMateRunner.captureState();
            challengeeSpeedrunner.captureState();
            challengeeMateRunner.captureState();

            TeamBattleSpeedrun teamRun = new TeamBattleSpeedrun(
                    GameMode.BATTLE,
                    challengerSpeedrunner,
                    challengerMateRunner,
                    challengeeSpeedrunner,
                    challengeeMateRunner,
                    stopWatch,
                    null
            );

            gameManager.registerRun(teamRun);
            initializeRun((Speedrun) teamRun);

            challenger.sendMessage(ChatColor.YELLOW + "Creating the world for your team...");
            challengee.sendMessage(ChatColor.YELLOW + "Creating the world for your team...");

            // Create two world-sets: one for each team. Use the team leaders as representatives.
            worldManager.createWorldsForPlayers(List.of(challenger, challengee), null, sets -> {
                WorldManager.WorldSet challengerWorldSet = sets.get(challenger.getUniqueId());
                WorldManager.WorldSet challengeeWorldSet = sets.get(challengee.getUniqueId());

                // Assign same world set to teammates on each team and set shared seed
                challengerSpeedrunner.setWorldSet(challengerWorldSet);
                challengerMateRunner.setWorldSet(challengerWorldSet);
                challengeeSpeedrunner.setWorldSet(challengeeWorldSet);
                challengeeMateRunner.setWorldSet(challengeeWorldSet);
                // Assign seed from the team's overworld via the spawn world's seed
                long seedLong = challengerWorldSet.getSpawn().getWorld().getSeed();
                teamRun.setSeed(Long.valueOf(seedLong));

                // Freeze all
                challengerSpeedrunner.freeze();
                challengerMateRunner.freeze();
                challengeeSpeedrunner.freeze();
                challengeeMateRunner.freeze();

                // Teleport
                challenger.teleport(challengerSpeedrunner.getWorldSet().getSpawn());
                challengerTeamMate.teleport(challengerMateRunner.getWorldSet().getSpawn());
                challengee.teleport(challengeeSpeedrunner.getWorldSet().getSpawn());
                challengeeTeamMate.teleport(challengeeMateRunner.getWorldSet().getSpawn());

                // Reset states
                challengerSpeedrunner.resetState();
                challengerMateRunner.resetState();
                challengeeSpeedrunner.resetState();
                challengeeMateRunner.resetState();

                startCountdown((Speedrun) teamRun, List.of(
                        challengerSpeedrunner,
                        challengerMateRunner,
                        challengeeSpeedrunner,
                        challengeeMateRunner
                ));
            });

            return;
        }

        // === FALLBACK to 1v1 ===
        // Setup stopwatch
        StopWatch stopWatch = new StopWatch();
        Speedrunner challengerSpeedrunner = new Speedrunner(challenger, stopWatch);
        Speedrunner challengeeSpeedrunner = new Speedrunner(challengee, stopWatch);

        // Capture the players' state - their inventory, levels, etc.
        challengerSpeedrunner.captureState();
        challengeeSpeedrunner.captureState();

        BattleSpeedrun battleSpeedrun = new BattleSpeedrun(
                GameMode.BATTLE,
                challengerSpeedrunner,
                challengeeSpeedrunner,
                stopWatch,
                null
        );
        gameManager.registerRun(battleSpeedrun);

        initializeRun((Speedrun) battleSpeedrun);

        challenger.sendMessage(ChatColor.YELLOW + "Creating the world...");
        challengee.sendMessage(ChatColor.YELLOW + "Creating the world...");
        worldManager.createWorldsForPlayers(List.of(challenger, challengee), null, sets -> {
            // Get the set of worlds (overworld, nether, end) for each of the two players
            WorldManager.WorldSet challengerWorldSet = sets.get(challenger.getUniqueId());
            WorldManager.WorldSet challengeeWorldSet = sets.get(challengee.getUniqueId());

            // Assign them the world sets and set the shared seed
            challengerSpeedrunner.setWorldSet(challengerWorldSet);
            challengeeSpeedrunner.setWorldSet(challengeeWorldSet);
            long seedLong2 = challengerWorldSet.getSpawn().getWorld().getSeed();
            battleSpeedrun.setSeed(Long.valueOf(seedLong2));

            // Freeze the players
            challengerSpeedrunner.freeze();
            challengeeSpeedrunner.freeze();

            // Teleport players
            challenger.teleport(challengerSpeedrunner.getWorldSet().getSpawn());
            challengee.teleport(challengeeSpeedrunner.getWorldSet().getSpawn());

            // Reset players' state (health, hunger, inventory, etc.)
            challengerSpeedrunner.resetState();
            challengeeSpeedrunner.resetState();

            startCountdown((Speedrun) battleSpeedrun, List.of(challengerSpeedrunner, challengeeSpeedrunner));
        });
    }

    /* ==========================================================
     *                       RESET BATTLE
     * ========================================================== */
    /**
     * Resets the worlds and state of a player in a {@link BattleSpeedrun}.
     *
     * <p>Teleports the player, recreates worlds, and restores state.</p>
     *
     * @param player the player requesting the reset
     */
    @Override
    public void reset(Player player) {
        // If not already in a speedrun
        Optional<Speedrun> optional = gameManager.getActiveRun(player);
        if (optional.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You are not in a speedrun!");
            return;
        }

        // Get the run
        BattleSpeedrun battleSpeedrun = (BattleSpeedrun) optional.get();

        // Get the speedrunner trying to reset
        Optional<Speedrunner> speedrunner = gameManager.getSpeedrunner(player);
        if (speedrunner.isEmpty()) return;

        // Get the seed of the existing world
        Long seed = battleSpeedrun.getSeed();

        player.sendMessage(ChatColor.YELLOW + "Resetting the world...");

        recreateWorldsForReset(speedrunner.get(), seed, () -> {});
    }

    /* ==========================================================
     *                       STOP BATTLE
     * ========================================================== */
    /**
     * Stops a {@link BattleSpeedrun}, showing results to both players.
     *
     * <p>Displays titles with winner/loser information and formatted run time.
     * Calls {@link GameModeManager#finishRun} for cleanup.</p>
     *
     * @param winner the player who won
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
        Speedrun run = (Speedrun) optional.get();

        // Update the state
        run.setState(Speedrun.State.FINISHED);

        // Get the final time
        String formattedTime = new TimeFormatter(run.getStopWatch())
                .withHours()
                .withSuperscriptMs()
                .format();

        if (run instanceof BattleSpeedrun) {
            BattleSpeedrun battleSpeedrun = (BattleSpeedrun) run;
            // Announce winner and loser (with times)
            winner.sendTitle(
                    ChatColor.GREEN + "You won! ",
                    ChatColor.GREEN + "With a time of: " +
                            ChatColor.ITALIC + ChatColor.GRAY + formattedTime,
                    10,
                    140,
                    20
            );
            getOpponent(battleSpeedrun, winner).sendTitle(
                    ChatColor.RED + "You lost! ",
                    ChatColor.GRAY + winner.getName() +
                            ChatColor.RED + " won with a time of: " +
                            ChatColor.ITALIC + ChatColor.GRAY + formattedTime,
                    10,
                    140,
                    20
            );

            finishRun(battleSpeedrun, 200);
            return;
        }

        if (run instanceof TeamBattleSpeedrun) {
            TeamBattleSpeedrun teamRun = (TeamBattleSpeedrun) run;

            // Determine which team the winner is on
            java.util.List<Player> teamAWinners = List.of(teamRun.getTeamA1().getPlayer(), teamRun.getTeamA2().getPlayer());
            java.util.List<Player> teamBWinners = List.of(teamRun.getTeamB1().getPlayer(), teamRun.getTeamB2().getPlayer());

            boolean winnerOnA = teamAWinners.contains(winner);

            // Send titles to each player depending on team
            if (winnerOnA) {
                // Team A wins
                for (Player p : teamAWinners) {
                    p.sendTitle(ChatColor.GREEN + "You won! ", ChatColor.GREEN + "With a time of: " + ChatColor.ITALIC + ChatColor.GRAY + formattedTime, 10, 140, 20);
                }
                for (Player p : teamBWinners) {
                    p.sendTitle(ChatColor.RED + "Your team lost! ", ChatColor.GRAY + winner.getName() + ChatColor.RED + " won with a time of: " + ChatColor.ITALIC + ChatColor.GRAY + formattedTime, 10, 140, 20);
                }
            } else {
                // Team B wins
                for (Player p : teamBWinners) {
                    p.sendTitle(ChatColor.GREEN + "You won! ", ChatColor.GREEN + "With a time of: " + ChatColor.ITALIC + ChatColor.GRAY + formattedTime, 10, 140, 20);
                }
                for (Player p : teamAWinners) {
                    p.sendTitle(ChatColor.RED + "Your team lost! ", ChatColor.GRAY + winner.getName() + ChatColor.RED + " won with a time of: " + ChatColor.ITALIC + ChatColor.GRAY + formattedTime, 10, 140, 20);
                }
            }

            finishRun(teamRun, 200);
            return;
        }
    }

    /* ==========================================================
     *                       ABORT BATTLE
     * ========================================================== */
    @Override
    public void abort(@NonNull Speedrun run, CommandSender sender, String reason) {
        if (run instanceof TeamBattleSpeedrun) {
            TeamBattleSpeedrun teamRun = (TeamBattleSpeedrun) run;

            // If the sender is a participant, the opposing team wins
            List<Player> players = gameManager.getAllPlayersInRun(run);
            if (sender instanceof Player && players.contains((Player) sender)) {
                Player leaver = (Player) sender;
                // Determine winning team
                List<Player> teamA = List.of(teamRun.getTeamA1().getPlayer(), teamRun.getTeamA2().getPlayer());
                boolean leaverOnA = teamA.contains(leaver);

                Player winner = leaverOnA ? teamRun.getTeamB1().getPlayer() : teamRun.getTeamA1().getPlayer();
                stop(winner);
                return;
            }
        }

        // Otherwise fall back to the default abort
        super.abort(run, sender, reason);
    }

    /* ==========================================================
     *                       HELPERS
     * ========================================================== */
    private Player getOpponent(BattleSpeedrun battleSpeedrun, Player player) {
        Player challenger = battleSpeedrun.getChallenger().getPlayer();
        if (challenger.equals(player)) return battleSpeedrun.getChallengee().getPlayer();
        return challenger;
    }
}
