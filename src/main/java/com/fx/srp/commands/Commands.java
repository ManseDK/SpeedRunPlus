package com.fx.srp.commands;

import lombok.Getter;


@Getter
public enum Commands {
    SRP("srp");

    private final String command;

    Commands(String command) { this.command = command; }

    // Convenience method to chain with a subcommand
    public String with(Subcommands subcommand) {
        return command + " " + subcommand.getSubcommand();
    }
}


