package com.drfeederino.telegramwebchecker.parsers;

import com.drfeederino.telegramwebchecker.entities.TrackingCode;
import com.drfeederino.telegramwebchecker.enums.TrackingProvider;
import com.drfeederino.telegramwebchecker.repository.TrackingCodeRepository;
import com.drfeederino.telegramwebchecker.services.WebCheckerBot;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

@Slf4j
@Service
public class RussianPostTrackingParser extends TrackingParser {

    private static final String INPUT_TRACK_CODE_SELECTOR = "div.TrackingInput__InputBlock-sc-g27vt2-1 > input";
    private static final String PACKAGE_RECEIVED_STRING = "получено";
    private static final String SEARCH_CODE_BUTTON_SELECTOR = "div.TrackingInput__InputBlock-sc-g27vt2-1 > button";
    private static final String SHOW_TABLE_BUTTON_SELECTOR = "div.TrackingCardHistory__Layout-sc-zdvopc-0 > div";
    private static final String SECOND_TRACKING_TABLE_SELECTOR = "div.TrackingCardHistory__Layout-sc-zdvopc-0 > div:nth-child(2)";
    private static final String THIRD_TRACKING_TABLE_SELECTOR = "div.TrackingCardHistory__Layout-sc-zdvopc-0 > div:nth-child(3)";
    private static final Pattern STATUS_PATTERN = Pattern.compile("(^\\W{1,}) ([0-9]{1,}) (\\p{L}{1,}) ([0-9]{4}), ([0-9]{2}:[0-9]{2}) ([0-9]{6}), (\\p{L}{1,}\\W{1,})");
    private static final String TRACKING_URL = "https://www.pochta.ru/tracking";

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
        WebDriverWait wait = new WebDriverWait(driver, 30);
        try {
            codes
                    .stream()
                    .filter(trackingCode -> trackingCode.getCode() != null)
                    .forEach(code -> {
                        driver.get(TRACKING_URL);
                        waitForPageLoad();
                        wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState").equals("complete"));
                        driver
                                .findElement(By.cssSelector(INPUT_TRACK_CODE_SELECTOR))
                                .sendKeys(code.getCode());
                        driver
                                .findElement(By.cssSelector(SEARCH_CODE_BUTTON_SELECTOR))
                                .click();

                        wait
                                .until(presenceOfElementLocated(By.cssSelector(SHOW_TABLE_BUTTON_SELECTOR)))
                                .click();

                        String secondElement = wait.until(presenceOfElementLocated(By.cssSelector(SECOND_TRACKING_TABLE_SELECTOR))).getText();
                        String thirdElement = wait.until(presenceOfElementLocated(By.cssSelector(THIRD_TRACKING_TABLE_SELECTOR))).getText();
                        Matcher matcher = STATUS_PATTERN.matcher(secondElement);
                        String result = matcher.matches() ? thirdElement : secondElement;

                        log.info("{}: parsing result is {}.", getClass().getSimpleName(), result);
                        compareStatusesAndUpdate(code, result);
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

    @SneakyThrows
    private void waitForPageLoad() {
        Thread.sleep(1500L);
    }

}
