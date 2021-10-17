package com.drfeederino.telegramwebchecker.enums;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public enum BotState {

    ADD("/add"),
    DELETE("/delete"),
    START("/start"),
    STATUS("/status"),
    CHANGE("/change"),
    STOP("/stop");

    private final String command;

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
        for (BotState v : values())
            if (v.getCommand().equalsIgnoreCase(value)) return v;
        return null;
    }

    public String getCommand() {
        return command;
    }

}
