package com.drfeederino.telegramwebchecker.enums;

public enum UserStatus {

    ADDING_TRACKING, // user is adding a new tracking number
    AWAITING_FOR_INPUT, // awaiting the additional info
    DELETING_TRACKING, // deleting tracking codes
    SELECTING_PROVIDER; // user needs to choose the provider


    public static UserStatus getEnum(String value) {
        for (UserStatus v : values())
            if (v.name().equalsIgnoreCase(value)) return v;
        return null;
    }

}
