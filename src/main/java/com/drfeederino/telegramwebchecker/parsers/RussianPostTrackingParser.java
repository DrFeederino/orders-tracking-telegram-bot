package com.drfeederino.telegramwebchecker.parsers;

import com.drfeederino.telegramwebchecker.entities.TrackingCode;
import com.drfeederino.telegramwebchecker.entities.TrackingProvider;
import com.drfeederino.telegramwebchecker.repository.TrackingCodeRepository;
import com.drfeederino.telegramwebchecker.services.WebCheckerBot;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

@Slf4j
@Service
public class RussianPostTrackingParser extends TrackingParser {

    private static final String TRACKING_URL = "https://www.pochta.ru/tracking#%s";
    private static final String TRACKING_TABLE_SELECTOR = "div.TrackingCardHistory__Layout-sc-zdvopc-0 > div:nth-child(2)";
    private static final String PACKAGE_RECEIVED_STRING = "получено";

    public RussianPostTrackingParser(WebCheckerBot webCheckerBot, TrackingCodeRepository trackingCodeRepository) {
        super(webCheckerBot, trackingCodeRepository);
    }

    @Override
    public void updateTrackingStatuses() {
        List<TrackingCode> codes = trackingCodeRepository.findAllByProvider(TrackingProvider.RUSSIAN_POST);
        if (codes.isEmpty()) {
            return;
        }
        FirefoxDriver driver = createHeadlessDriver();
        WebDriverWait wait = new WebDriverWait(driver, 120);
        try {
            codes
                    .stream()
                    .filter(trackingCode -> trackingCode.getCode() != null)
                    .forEach(code -> {
                        driver.get(String.format(TRACKING_URL, code.getCode()));
                        WebElement webElement = wait.until(presenceOfElementLocated(By.cssSelector(TRACKING_TABLE_SELECTOR)));
                        log.info("{}: parsing result is {}.", getClass().getSimpleName(), webElement.getText());
                        compareStatusesAndUpdate(code, webElement.getText());
                    });
        } finally {
            driver.quit();
        }
    }

    @Override
    protected void compareStatusesAndUpdate(TrackingCode trackingCode, String status) {
        super.compareStatusesAndUpdate(trackingCode, status);
        if (status.toLowerCase().contains(PACKAGE_RECEIVED_STRING)) {
            super.deleteTrackingIfPackageIsReceived(trackingCode);
        }
    }

}
