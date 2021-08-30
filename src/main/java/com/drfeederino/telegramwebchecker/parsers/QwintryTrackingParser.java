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
public class QwintryTrackingParser extends TrackingParser {

    private static final String TRACKING_URL = "https://logistics.qwintry.com/ru/track?tracking=%s";
    private static final String CSS_SELECTOR = "#w0 > table > tbody > tr:last-child";
    private static final String PACKAGE_RECEIVED_STRING = "доставлено";
    private static final String PACKAGE_HANDED_STRING = "вручен";

    public QwintryTrackingParser(WebCheckerBot webCheckerBot, TrackingCodeRepository trackingCodeRepository) {
        super(webCheckerBot, trackingCodeRepository);
    }

    @Override
    public void updateTrackingStatuses() {
        List<TrackingCode> codes = trackingCodeRepository.findAllByProvider(TrackingProvider.QWINTRY);
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
                        By table = By.cssSelector(CSS_SELECTOR);
                        WebElement webElement = wait.until(presenceOfElementLocated(table));
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
        if (status.toLowerCase().contains(PACKAGE_HANDED_STRING) || status.toLowerCase().contains(PACKAGE_RECEIVED_STRING)) {
            super.deleteTrackingIfPackageIsReceived(trackingCode);
        }
    }

}
