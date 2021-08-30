package com.drfeederino.telegramwebchecker.entities;

public class BotMessages {

    private BotMessages() {
    }

    public static final String WELCOME_MESSAGE = "Hello, %s! Send me a tracking code with /add command and I'll do the rest.";
    public static final String ENCOURAGEMENT_ADD_CODES = "Bring it on.";
    public static final String SELECT_TRACKING_DELETE = "Send the code you want to delete.";
    public static final String WAIT_FOR_ME = "Fetching news...";
    public static final String EMPTY_LAST_STATUS = "unknown";
    public static final String STATUS_UNKNOWN_TIP = "If you ever wondered what <i>'unknown'</i> means, well, no surprises here. I'm a bit lazy, will check later.";
    public static final String LAST_STATUS_FORMAT = "Package %s.\nFrom: %s.\nLatest status: %s.\n";
    public static final String SOMETHING_WENT_TERRIBLY_WRONG = "Sorry, something went wrong. Terribly wrong.";
    public static final String SUCCESSFULLY_ADDED = "Done.";
    public static final String SELECT_TRACKING_PROVIDER = "Could you help me out with the tracking provider?";
    public static final String UNKNOWN_PROVIDER = "Hmm, I don't know such provider, retry with a valid provider from the list.";
    public static final String NEED_EMAIL = "For tracking of Samsung orders, please, provide an email you used.";
    public static final String FAIL_CODE_ADDED = "Sorry, I cannot guarantee to reliably track such codes.";
    public static final String GOODBYE_MESSAGE = "All your data has been purged. Thank you and have a nice day, human.";
    public static final String ENCOURAGE_COMMAND_USAGE = "I'd suggest using that little list of commands. Heard it was very useful.";
    public static final String NO_TRACKING_CODES = "You don't have anything added yet.";
    public static final String ADDITIONAL_ALL_DELETE_OPTION = "Additionally, you can type 'all' to delete all tracking numbers.";
    public static final String STATUS_NOT_ADDED = "your package hasn't been found in the provided system for now";

}
