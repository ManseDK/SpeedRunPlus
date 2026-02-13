package com.fx.srp.model.run;

import com.fx.srp.model.player.Speedrunner;
import com.fx.srp.util.ui.TimerUtil;
import com.fx.srp.commands.GameMode;
import lombok.Getter;
import org.apache.commons.lang.time.StopWatch;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Represents a team battle speedrun (2v2), with two players on each team.
 */
public class TeamBattleSpeedrun extends Speedrun {

    @Getter
    private final Speedrunner teamA1;

    @Getter
    private final Speedrunner teamA2;

    @Getter
    private final Speedrunner teamB1;

    @Getter
    private final Speedrunner teamB2;

    public TeamBattleSpeedrun(GameMode gameMode,
                              Speedrunner teamA1,
                              Speedrunner teamA2,
                              Speedrunner teamB1,
                              Speedrunner teamB2,
                              StopWatch stopWatch,
                              Long seed) {
        super(gameMode, teamA1, stopWatch, seed);
        this.teamA1 = teamA1;
        this.teamA2 = teamA2;
        this.teamB1 = teamB1;
        this.teamB2 = teamB2;
    }

    @Override
    public void initializeTimers() {
        TimerUtil.createTimer(List.of(
                teamA1.getPlayer(),
                teamA2.getPlayer(),
                teamB1.getPlayer(),
                teamB2.getPlayer()
        ), getStopWatch());
    }

    @Override
    public List<Speedrunner> getSpeedrunners() {
        return List.of(teamA1, teamA2, teamB1, teamB2);
    }

    @Override
    public void onPlayerLeave(Player leaver) {
        // If any player leaves, abort and declare the other team the winner
        gameMode.getManager().abort(this, leaver, "A player has left the team battle!");
    }
}

