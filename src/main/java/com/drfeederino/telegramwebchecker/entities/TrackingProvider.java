package com.drfeederino.telegramwebchecker.entities;

public enum TrackingProvider {
    QWINTRY("Qwintry"),
    SAMSUNG("Russian Samsung Store"),
    RUSSIAN_POST("Russian Post"),
    CSE("Courier Service Expert");

    private final String provider;

    TrackingProvider(String provider) {
        this.provider = provider;
    }

    public String getProvider() {
        return this.provider;
    }

    public static boolean contains(String test) {

        for (TrackingProvider provider : TrackingProvider.values()) {
            if (provider.name().equalsIgnoreCase(test)) {
                return true;
            }
        }

        return false;
    }

    public static TrackingProvider getEnum(String value) {
        for(TrackingProvider v : values())
            if(v.getProvider().equalsIgnoreCase(value)) return v;
        return null;
    }
}
