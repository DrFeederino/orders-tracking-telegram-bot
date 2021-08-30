package com.drfeederino.telegramwebchecker.entities;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum BotState {
    START("/start"),
    DELETE("/delete"),
    STOP("/stop"),
    ADD("/add"),
    STATUS("/status");

    private final String command;

    public String getCommand() {
        return command;
    }

    BotState(String command) {
        this.command = command;
    }

    public static boolean contains(String command) {

        for (BotState state : BotState.values()) {
            if (state.getCommand().equalsIgnoreCase(command)) {
                return true;
            }
        }

        return false;
    }

    public static BotState getEnum(String value) {
        for(BotState v : values())
            if(v.getCommand().equalsIgnoreCase(value)) return v;
        return null;
    }
}
