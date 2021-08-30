package com.drfeederino.telegramwebchecker.entities;

public enum UserStatus {
    ADDING_TRACKING, // user is adding a new tracking number
    DELETING_TRACKING, // deleting tracking codes
    AWAITING_FOR_INPUT, // awaiting for the additional info
    SELECTING_PROVIDER, // user needs to choose the provider
}
