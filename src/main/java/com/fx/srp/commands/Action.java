package com.fx.srp.commands;

import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.bukkit.parsers.PlayerArgument;
import cloud.commandframework.context.CommandContext;
import com.fx.srp.managers.GameManager;
import lombok.Getter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

@Getter
public enum Action {

    START((gm, ctx) -> {
        Player sender = (Player) ctx.getSender();
        GameMode gameMode = ctx.get(CommandRegistry.ContextKeys.GAME_MODE.getKey());
        gameMode.getManager().start(sender);
    }),

    RESET((gm, ctx) -> {
        Player sender = (Player) ctx.getSender();
        GameMode gameMode = ctx.get(CommandRegistry.ContextKeys.GAME_MODE.getKey());
        gameMode.getManager().reset(sender);
    }),

    STOP((gm, ctx) -> {
        Player sender = (Player) ctx.getSender();
        gm.abortRun(sender);
    }),

    REQUEST((gm, ctx) -> {
        Player sender = (Player) ctx.getSender();
        GameMode gameMode = ctx.get(CommandRegistry.ContextKeys.GAME_MODE.getKey());
        Player target = ctx.get(CommandRegistry.ContextKeys.PLAYER_TARGET.getKey());
        gameMode.getManager().asMultiplayerManager().ifPresent(gameModeManager ->
                gameModeManager.request(sender, target, gameMode)
        );
    }, PlayerArgument.of(CommandRegistry.ContextKeys.PLAYER_TARGET.getKey())),

    TEAM((gm, ctx) -> {
        // /srp <gamemode> team <player> - select a teammate for team-based battles
        Player sender = (Player) ctx.getSender();
        Player target = ctx.get(CommandRegistry.ContextKeys.PLAYER_TARGET.getKey());
        if (sender.equals(target)) {
            sender.sendMessage("You cannot set yourself as your teammate.");
            return;
        }
        gm.setSelectedTeammate(sender, target);
        sender.sendMessage("§eTeammate set to §f" + target.getName() + "§e. When you initiate/accept a battle both teams will be used if available.");
    }, PlayerArgument.of(CommandRegistry.ContextKeys.PLAYER_TARGET.getKey())),

    INVITE((gm, ctx) -> {
        // /srp <gamemode> invite - invite your selected teammate to join you
        Player sender = (Player) ctx.getSender();
        GameMode gameMode = ctx.get(CommandRegistry.ContextKeys.GAME_MODE.getKey());
        java.util.Optional<Player> mate = gm.getSelectedTeammate(sender);
        if (mate.isEmpty()) {
            sender.sendMessage("§cYou have no selected teammate. Use §e/srp <mode> team <player>§c to set one.");
            return;
        }
        Player teammate = mate.get();
        // Send a request to the teammate using the multiplayer manager
        gameMode.getManager().asMultiplayerManager().ifPresent(manager ->
                manager.request(sender, teammate, gameMode)
        );
    }),

    ACCEPT((gm, ctx) -> {
        GameMode gameMode = ctx.get(CommandRegistry.ContextKeys.GAME_MODE.getKey());
        gameMode.getManager().asMultiplayerManager().ifPresent(gameModeManager ->
                gameModeManager.accept((Player) ctx.getSender())
        );
    }),

    DECLINE((gm, ctx) -> {
        GameMode gameMode = ctx.get(CommandRegistry.ContextKeys.GAME_MODE.getKey());
        Player target = ctx.get(CommandRegistry.ContextKeys.PLAYER_TARGET.getKey());
        gameMode.getManager().asMultiplayerManager().ifPresent(gameModeManager ->
                gameModeManager.decline(target)
        );
    });

    private final BiConsumer<GameManager, CommandContext<CommandSender>> executor;
    private final List<CommandArgument<CommandSender, ?>> arguments;

    /**
     * Creates an action for the SRP Commands.
     *
     * @param executor the executor for the command
     */
    @SafeVarargs
    Action(BiConsumer<GameManager, CommandContext<CommandSender>> executor, CommandArgument<CommandSender, ?>... args) {
        this.executor = executor;
        this.arguments = new ArrayList<>();
        if (args != null) this.arguments.addAll(Arrays.asList(args));
    }
}
