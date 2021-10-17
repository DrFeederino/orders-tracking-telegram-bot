package com.drfeederino.telegramwebchecker.enums;

public enum TrackingProvider {

    QWINTRY("Qwintry"),
    SAMSUNG("Russian Samsung Store"),
    RUSSIAN_POST("Russian Post"),
    CSE("Courier Service Expert");

    private final String provider;

    TrackingProvider(String provider) {
        this.provider = provider;
    }

    public static boolean contains(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        String trimmedValue = value.trim();
        for (TrackingProvider provider : TrackingProvider.values()) {
            if (provider.getProvider().equalsIgnoreCase(trimmedValue)) {
                return true;
            }
        }

        return false;
    }

    public static TrackingProvider getEnum(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        String trimmedValue = value.trim();
        for (TrackingProvider v : values())
            if (v.getProvider().equalsIgnoreCase(trimmedValue)) return v;
        return null;
    }

    public String getProvider() {
        return this.provider;
    }

}
