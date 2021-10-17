package com.drfeederino.telegramwebchecker.constants;

import java.util.regex.Pattern;

public class Patterns {
    public static final Pattern CSE_TRACK_PATTERN = Pattern.compile("[0-9]{3}-[0-9]{6,8}-[0-9]{6,8}");
    public static final Pattern INTERNATIONAL_POST_STANDARD_TRACK_NUMBER = Pattern.compile("[A-Za-z]{2}[0-9]{9}[A-Za-z]{2}");
    public static final Pattern QWINTRY_TRACK_PATTERN = Pattern.compile("QR[0-9]{6,12}");
    public static final Pattern SAMSUNG_ORDER_NUMBER = Pattern.compile("RU[0-9]{6}-[0-9]{8}");


    private Patterns() {
    }

}
