package com.drfeederino.telegramwebchecker.parsers;

import com.drfeederino.telegramwebchecker.entities.TrackingCode;
import com.drfeederino.telegramwebchecker.enums.TrackingProvider;
import com.drfeederino.telegramwebchecker.repository.TrackingCodeRepository;
import com.drfeederino.telegramwebchecker.services.WebCheckerBot;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.drfeederino.telegramwebchecker.constants.BotMessages.STATUS_IDK;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

@Slf4j
@Service
public class CSETrackingParser extends TrackingParser {

    private static final String LATEST_STATUS_SELECTOR = "#track-box > div > div.offerlist-wrap > div > div > div.offerlist-item-content > div:nth-child(1) > div.offerlist-item-table > div.table-row > div:nth-child(1) > div";
    private static final String PACKAGE_RECEIVED_STRING = "доставка завершена";
    private static final String RECEIPT_RECEIVED_STRING = "выбит кассовый чек";
    private static final String TRACKING_URL = "https://www.cse.ru/spb/track/?numbers=%s";

    public CSETrackingParser(WebCheckerBot webCheckerBot, TrackingCodeRepository trackingCodeRepository) {
        super(webCheckerBot, trackingCodeRepository);
    }

    @Override
    public void updateTrackingStatuses() {
        List<TrackingCode> codes = trackingCodeRepository.findAllByProvider(TrackingProvider.CSE);
        if (codes.isEmpty()) {
            return;
        }
        FirefoxDriver driver = createHeadlessDriver();
        WebDriverWait wait = new WebDriverWait(driver, 10);
        try {
            codes
                    .stream()
                    .filter(trackingCode -> trackingCode.getCode() != null)
                    .forEach(code -> {
                        driver.get(String.format(TRACKING_URL, code.getCode()));
                        try {
                            String result = wait.until(presenceOfElementLocated(By.cssSelector(LATEST_STATUS_SELECTOR))).getText();
                            if (result != null) {
                                result = result
                                        .replace("\n", " ")
                                        .replace("\\s{2,}", " ")
                                        .trim();
                            }
                            log.info("{}: parsing result is {}.", getClass().getSimpleName(), result);
                            compareStatusesAndUpdate(code, result);
                        } catch (RuntimeException e) {
                            log.info("{}: no package information for {}.", getClass().getSimpleName(), code.getCode());
                            compareStatusesAndUpdate(code, STATUS_IDK);
                        }

                    });
        } finally {
            driver.quit();
        }
    }

    @Override
    protected void compareStatusesAndUpdate(TrackingCode trackingCode, String status) {
        super.compareStatusesAndUpdate(trackingCode, status);
        if (status.toLowerCase().contains(RECEIPT_RECEIVED_STRING) || status.toLowerCase().contains(PACKAGE_RECEIVED_STRING)) {
            super.deleteTrackingIfPackageIsReceived(trackingCode);
        }
    }

}
