package com.drfeederino.telegramwebchecker.parsers;

import com.drfeederino.telegramwebchecker.entities.TrackingCode;
import com.drfeederino.telegramwebchecker.repository.TrackingCodeRepository;
import com.drfeederino.telegramwebchecker.services.WebCheckerBot;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxDriverLogLevel;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

public abstract class TrackingParser {

    private static final String UPDATE_MESSAGE = "Update for <b>%s</b>.%nNew status is:%n%s.";
    private static final String UPDATE_SHIPPED_MESSAGE = "Update for <b>%s</b>.%n%s has shipped your package!%nThe tracking has been updated to track it by <b>%s</b>.";
    private static final String UPDATE_PARTLY_SHIPPED_MESSAGE = "Update for <b>%s</b>.%n%s has shipped your package <i>partly</it>!%nA new tracking has been created to track it by <b>%s</b>.";
    private static final String UPDATE_DELIVERED_MESSAGE = "Your package <b>%s</b> has been received. Less work for me. Don't worry, I also deleted it on my end.";
    protected final WebCheckerBot webCheckerBot;
    protected final TrackingCodeRepository trackingCodeRepository;

    @Autowired
    protected TrackingParser(WebCheckerBot webCheckerBot, TrackingCodeRepository trackingCodeRepository) {
        this.webCheckerBot = webCheckerBot;
        this.trackingCodeRepository = trackingCodeRepository;
    }

    private static String buildUpdateMessage(String trackCode, String status) {
        return String.format(UPDATE_MESSAGE, trackCode, status);
    }

    protected static String buildMessageForShipped(String trackCode, String seller, String provider) {
        return String.format(UPDATE_SHIPPED_MESSAGE, trackCode, seller, provider);
    }

    protected static String buildMessageForSomeShipped(String trackCode, String seller, String provider) {
        return String.format(UPDATE_PARTLY_SHIPPED_MESSAGE, trackCode, seller, provider);
    }

    protected static String buildMessageForReceived(String trackCode) {
        return String.format(UPDATE_DELIVERED_MESSAGE, trackCode);
    }

    @Scheduled(fixedRate = 1000 * 60 * 60)
    private void scheduleUpdate() {
        updateTrackingStatuses();
    }

    protected abstract void updateTrackingStatuses();

    protected void compareStatusesAndUpdate(TrackingCode trackingCode, String status) {
        if (status != null && !status.equalsIgnoreCase(trackingCode.getLastStatus())) {
            trackingCode.setLastStatus(status);
            trackingCodeRepository.save(trackingCode);
            webCheckerBot.sendUpdate(trackingCode.getTelegramUser().getId(), buildUpdateMessage(trackingCode.getCode(), status));
        }
    }

    protected void deleteTrackingIfPackageIsReceived(TrackingCode trackingCode) {
        trackingCodeRepository.deleteById(trackingCode.getId());
        webCheckerBot.sendUpdate(trackingCode.getTelegramUser().getId(), buildMessageForReceived(trackingCode.getCode()));
    }

    protected FirefoxDriver createHeadlessDriver() {
        FirefoxOptions options = new FirefoxOptions();
        options.setHeadless(true);
        options.setLogLevel(FirefoxDriverLogLevel.FATAL);
        return new FirefoxDriver(options);
    }

}
