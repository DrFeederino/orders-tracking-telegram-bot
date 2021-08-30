package com.drfeederino.telegramwebchecker.parsers;

import com.drfeederino.telegramwebchecker.entities.TrackingCode;
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
import java.util.stream.Collectors;

import static com.drfeederino.telegramwebchecker.entities.TrackingProvider.CSE;
import static com.drfeederino.telegramwebchecker.entities.TrackingProvider.SAMSUNG;
import static org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated;

@Slf4j
@Service
public class ShopSamsungTrackingParser extends TrackingParser {

    private static final String TRACKING_URL = "https://shop.samsung.com/ru/mypage/orders";
    private static final String ORDER_NUMBER_SELECTOR = "mat-input-0";
    private static final String ORDER_EMAIL_SELECTOR = "mat-input-1";
    private static final String BUTTON_SELECTOR = "body > app-root > cx-storefront > main > cx-page-layout > cx-page-slot > app-orders > app-order-guest-lookup > div > div > form > div.guest-lookup__form-button-container > button";
    private static final String ORDER_TABLE_SELECTOR = "body > app-root > cx-storefront > main > cx-page-layout > cx-page-slot > app-orders > div > div > app-order-item-guest > div > app-order-item-entry > div > div.order-item-entry__info > div.order-item-entry-summary > div.order-item-entry-summary__row.order-item-entry-status.ng-star-inserted > span";
    private static final String SAMSUNG_HAS_SHIPPED = "dispatched";
    private static final String MORE_INFO_BUTTON_SELECTOR = "body > app-root > cx-storefront > main > cx-page-layout > cx-page-slot > app-orders > div > div > app-order-item-guest > div > div.order-item__view-detail > button";
    private static final String ORDER_ITEMS_H2_CLASSNAME = "order-item-entry__name";
    private static final String ORDER_ITEMS_STATUS_CLASSNAME = "order-item-entry__order-status-text";
    private static final String ORDER_ITEMS_QUANTITY = "div > div.order-item-entry__info > div:nth-child(3) > div:nth-child(2)";
    private static final String ORDER_ITEMS_FORMAT = "%sx <b>%s</b> %s.\n";
    private static final String SUMMARY_ORDER_FORMAT = "Overall status <b>%s</b>";
    private static final String SUM_TO_DELETE = "Сумма";
    private static final String ON_ITS_WAY = "в пути";
    private static final String IN_PROGRESS = "обрабатывается";
    private static final String PREPARING_TO_SHIP = "подготовка доставки";

    public ShopSamsungTrackingParser(WebCheckerBot webCheckerBot, TrackingCodeRepository trackingCodeRepository) {
        super(webCheckerBot, trackingCodeRepository);
    }

    @Override
    public void updateTrackingStatuses() {
        List<TrackingCode> codes = trackingCodeRepository.findAllByProvider(SAMSUNG);
        if (codes.isEmpty()) {
            return;
        }
        FirefoxDriver driver = createHeadlessDriver();
        WebDriverWait wait = new WebDriverWait(driver, 120);
        try {
            codes
                    .stream()
                    .filter(trackingCode -> trackingCode.getCode() != null && trackingCode.getEmail() != null) // can't proceed without email
                    .forEach(code -> {
                        driver.get(TRACKING_URL);
                        wait.until(presenceOfElementLocated(By.id(ORDER_NUMBER_SELECTOR)));
                        driver
                                .findElement(By.id(ORDER_NUMBER_SELECTOR))
                                .sendKeys(code.getCode());
                        driver
                                .findElement(By.id(ORDER_EMAIL_SELECTOR))
                                .sendKeys(code.getEmail());
                        driver
                                .findElement(By.cssSelector(BUTTON_SELECTOR))
                                .click();

                        String orderResult = wait.until(presenceOfElementLocated(By.cssSelector(ORDER_TABLE_SELECTOR))).getText().toLowerCase();
                        driver
                                .findElement(By.cssSelector(MORE_INFO_BUTTON_SELECTOR))
                                .click();

                        List<String> orderedItems = driver.findElements(By.className(ORDER_ITEMS_H2_CLASSNAME))
                                .stream()
                                .map(WebElement::getText)
                                .map(String::toLowerCase)
                                .collect(Collectors.toUnmodifiableList());
                        List<String> statusItems = driver.findElements(By.className(ORDER_ITEMS_STATUS_CLASSNAME))
                                .stream()
                                .map(WebElement::getText)
                                .map(String::toLowerCase)
                                .collect(Collectors.toUnmodifiableList());
                        List<String> quantityItems = driver.findElements(By.cssSelector(ORDER_ITEMS_QUANTITY))
                                .stream()
                                .map(WebElement::getText)
                                .map(status -> status
                                        .substring(status.indexOf(":") + 1, status.indexOf(SUM_TO_DELETE))
                                        .trim())
                                .collect(Collectors.toUnmodifiableList());

                        StringBuilder status = new StringBuilder();

                        for (int i = 0; i < orderedItems.size(); i++) {
                            status.append(String.format(
                                    ORDER_ITEMS_FORMAT,
                                    quantityItems.get(i),
                                    orderedItems.get(i),
                                    statusItems.get(i)
                            ));
                        }
                        status.append(String.format(SUMMARY_ORDER_FORMAT, orderResult));

                        log.info("{}: parsing result is {}.", getClass().getSimpleName(), status);
                        compareStatusesAndUpdate(code, status.toString());
                        updateTrackingCodeIfSamsungHasShipped(code, status.toString());
                    });
        } finally {
            driver.quit();
        }
    }

    private void updateTrackingCodeIfSamsungHasShipped(TrackingCode trackingCode, String newStatus) {
        if (
                newStatus.contains(String.format(SUMMARY_ORDER_FORMAT, SAMSUNG_HAS_SHIPPED)) &&
                !newStatus.contains(IN_PROGRESS) &&
                !newStatus.contains(PREPARING_TO_SHIP)
        ) {
            trackingCode.setProvider(CSE);
            trackingCodeRepository.save(trackingCode);
            webCheckerBot.sendUpdate(trackingCode.getTelegramUser().getId(), buildMessageForShipped(trackingCode.getCode(), SAMSUNG.getProvider(), CSE.getProvider()));
        } else if (newStatus.contains(ON_ITS_WAY) || newStatus.contains(PREPARING_TO_SHIP)) {
            TrackingCode newTrackingCode = new TrackingCode();
            newTrackingCode.setProvider(CSE);
            newTrackingCode.setEmail(trackingCode.getEmail());
            newTrackingCode.setTelegramUser(trackingCode.getTelegramUser());
            newTrackingCode.setLastStatus(trackingCode.getLastStatus());
            trackingCodeRepository.save(newTrackingCode);
            webCheckerBot.sendUpdate(newTrackingCode.getTelegramUser().getId(), buildMessageForSomeShipped(newTrackingCode.getCode(), SAMSUNG.getProvider(), CSE.getProvider()));
        }
    }

}
