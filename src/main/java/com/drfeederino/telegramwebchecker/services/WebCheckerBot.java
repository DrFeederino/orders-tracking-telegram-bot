package com.drfeederino.telegramwebchecker.services;

import com.drfeederino.telegramwebchecker.entities.BotState;
import com.drfeederino.telegramwebchecker.entities.TelegramUser;
import com.drfeederino.telegramwebchecker.entities.TrackingCode;
import com.drfeederino.telegramwebchecker.entities.TrackingProvider;
import com.drfeederino.telegramwebchecker.repository.TelegramUserRepository;
import com.drfeederino.telegramwebchecker.repository.TrackingCodeRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.drfeederino.telegramwebchecker.entities.BotMessages.*;
import static com.drfeederino.telegramwebchecker.entities.TrackingProvider.*;
import static com.drfeederino.telegramwebchecker.entities.UserStatus.*;

@Service
@Slf4j
public class WebCheckerBot extends TelegramLongPollingBot {

    private static final Pattern INTERNATIONAL_POST_STANDARD_TRACK_NUMBER = Pattern.compile("[A-Za-z]{2}[0-9]{9}[A-Za-z]{2}");
    private static final Pattern SAMSUNG_ORDER_NUMBER = Pattern.compile("RU[0-9]{6}-[0-9]{8}");
    private static final Pattern QWINTRY_TRACK_PATTERN = Pattern.compile("QR[0-9]{6,12}");
    private static final Pattern CSE_TRACK_PATTERN = Pattern.compile("[0-9]{3}-[0-9]{6,8}-[0-9]{6,8}");

    private final TelegramUserRepository userRepository;
    private final TrackingCodeRepository trackingRepository;

    private static final Set<TrackingProvider> REQUIRE_ADDITIONAL_INFO = Set.of(SAMSUNG);


    @Value("${BOT_NAME}")
    private String botName;
    @Value("${BOT_TOKEN}")
    private String botToken;

    @Autowired
    public WebCheckerBot(
            TelegramUserRepository repository,
            TrackingCodeRepository trackingRepository
    ) {
        this.userRepository = repository;
        this.trackingRepository = trackingRepository;
    }

    @Override
    public String getBotUsername() {
        return botName;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @PostConstruct
    public void register() {
        try {
            log.debug("{}: registering bot...", getClass().getSimpleName());
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(this);
            log.debug("{}: successfully registered bot.", getClass().getSimpleName());
        } catch (TelegramApiException e) {
            log.debug("{}: an error occurred {}", getClass().getSimpleName(), e.getMessage());
            e.printStackTrace();
        }
    }

    @SneakyThrows
    @Override
    public void onUpdateReceived(Update update) {
        log.info("{}: received update - {}.", getClass().getSimpleName(), update);
        if (update.hasMessage() && BotState.contains(update.getMessage().getText())) {
            handleUserState(update);
        } else if (update.hasMessage()) {
            handleUserMessageForStatus(update);
        } else {
            execute(buildMessage(update.getMessage().getChatId(), ENCOURAGE_COMMAND_USAGE));
        }
    }

    @SneakyThrows
    private void handleUserMessageForStatus(Update update) {
        TelegramUser telegramUser = getTelegramUser(update.getMessage().getChatId());
        if (telegramUser != null && telegramUser.getStatus() != null) {
            switch (telegramUser.getStatus()) {
                case ADDING_TRACKING: {
                    handleAddingTracking(update.getMessage().getChatId(), update.getMessage().getText());
                    break;
                }
                case SELECTING_PROVIDER: {
                    handleSelectProvider(update.getMessage().getChatId(), update.getMessage().getText());
                    break;
                }
                case AWAITING_FOR_INPUT: {
                    handleAdditionalInfo(update.getMessage().getChatId(), update.getMessage().getText());
                    break;
                }
                case DELETING_TRACKING: {
                    handleDeleteTracking(update.getMessage().getChatId(), update.getMessage().getText());
                    break;
                }
            }
        } else {
            execute(buildMessage(update.getMessage().getChatId(), ENCOURAGE_COMMAND_USAGE));
        }
    }

    @SneakyThrows
    private void handleAdditionalInfo(Long chatId, String text) {
        trackingRepository.findTopByTelegramUserIdAndEmailNull(chatId).ifPresent(trackingCode -> {
            trackingCode.setEmail(text);
            trackingRepository.save(trackingCode);
            userRepository.findById(chatId)
                    .ifPresent(user -> {
                        user.setStatus(null);
                        userRepository.save(user);
                    });
        });
        execute(buildMessage(chatId, SUCCESSFULLY_ADDED));
    }

    @SneakyThrows
    private void handleDeleteTracking(Long chatId, String message) {
        if (message == null) {
            return;
        }
        if (message.contains("all")) {
            trackingRepository.deleteAllByTelegramUserId(chatId);
            execute(buildMessage(chatId, SUCCESSFULLY_ADDED));
        } else {
            trackingRepository.findAllByTelegramUserId(chatId)
                    .stream()
                    .filter(code -> code.getCode().equalsIgnoreCase(message))
                    .findFirst()
                    .ifPresentOrElse(result -> {
                        trackingRepository.deleteById(result.getId());
                        userRepository.findById(chatId).ifPresent(user -> {
                            user.setStatus(null);
                            userRepository.save(user);
                        });
                        try {
                            execute(buildMessage(chatId, SUCCESSFULLY_ADDED));
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    }, () -> {
                        try {
                            execute(buildMessage(chatId, SOMETHING_WENT_TERRIBLY_WRONG));
                        } catch (TelegramApiException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    @SneakyThrows
    private void handleSelectProvider(Long chatId, String message) {
        if (TrackingProvider.contains(message)) {
            TrackingProvider trackingProvider = TrackingProvider.getEnum(message);
            TrackingCode usersLastTracking = trackingRepository.findTopByTelegramUserIdAndProviderNull(chatId);
            usersLastTracking.setProvider(trackingProvider);
            trackingRepository.save(usersLastTracking);
            userRepository.findById(chatId).ifPresent(user -> {
                user.setStatus(null);
                userRepository.save(user);
            });
            execute(buildMessage(chatId, SUCCESSFULLY_ADDED));
        } else {
            execute(buildMessage(chatId, UNKNOWN_PROVIDER));
            sendAvailableProviders(chatId);
        }
    }

    private TelegramUser getTelegramUser(Long chatId) {
        return userRepository.findById(chatId).orElse(null);
    }

    private void handleUserState(Update update) throws TelegramApiException {
        Message message = update.getMessage();
        switch (parseMessage(update.getMessage().getText())) {
            case START: {
                getUserOrCreate(message.getChatId());
                execute(buildMessage(message.getChatId(), String.format(WELCOME_MESSAGE, update.getMessage().getChat().getUserName())));
                break;
            }
            case ADD: {
                execute(buildMessage(message.getChatId(), ENCOURAGEMENT_ADD_CODES));
                updateAddTrackCode(message.getChatId());
                break;
            }
            case STATUS:
                execute(buildMessage(message.getChatId(), WAIT_FOR_ME));
                getLatestStatuses(message.getChatId());
                break;
            case STOP: {
                execute(buildMessage(message.getChatId(), GOODBYE_MESSAGE));
                deleteUser(message.getChatId());
                break;
            }
            case DELETE: {
                execute(buildMessage(message.getChatId(), SELECT_TRACKING_DELETE));
                deleteTracking(message.getChatId());
                break;
            }
        }
    }

    @SneakyThrows
    private void deleteTracking(Long chatId) {
        userRepository.findById(chatId)
                .ifPresent(user -> {
                    user.setStatus(DELETING_TRACKING);
                    userRepository.save(user);
                });
        List<TrackingCode> values = trackingRepository.findAllByTelegramUserId(chatId);
        if (values.isEmpty()) {
            userRepository.findById(chatId)
                    .ifPresent(user -> {
                        user.setStatus(null);
                        userRepository.save(user);
                    });
            execute(buildMessage(chatId, NO_TRACKING_CODES));
            return;
        }
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            message.append(i + 1)
                    .append(" ")
                    .append(values.get(i).getCode())
                    .append("\n");
        }
        execute(buildMessage(chatId, message.toString()));
        execute(buildMessage(chatId, ADDITIONAL_ALL_DELETE_OPTION));
    }

    private void updateAddTrackCode(Long chatId) {
        userRepository.findById(chatId)
                .ifPresent(user -> {
                    user.setStatus(ADDING_TRACKING);
                    userRepository.save(user);
                });
    }

    @SneakyThrows
    private void getLatestStatuses(Long chatId) {
        List<TrackingCode> list = trackingRepository.findAllByTelegramUserId(chatId);
        if (list.isEmpty()) {
            execute(buildMessage(chatId, NO_TRACKING_CODES));
        }
        for (TrackingCode trackingCode : list) {
            String lastStatus = trackingCode.getLastStatus();
            execute(buildMessage(chatId,
                    String.format(
                            LAST_STATUS_FORMAT,
                            trackingCode.getCode(),
                            trackingCode.getProvider().getProvider(),
                            lastStatus != null ? lastStatus : EMPTY_LAST_STATUS
                    )
            ));
        }
        execute(buildMessage(chatId, STATUS_UNKNOWN_TIP));
    }

    private void deleteUser(Long chatId) {
        trackingRepository.deleteAllByTelegramUserId(chatId);
        userRepository.deleteById(chatId);
    }

    private BotState parseMessage(String text) {
        BotState state = BotState.getEnum(text);
        return state != null ? state : BotState.START;
    }

    @SneakyThrows
    public void sendUpdate(Long id, String updateMessage) {
        execute(buildMessage(id, updateMessage));
    }


    @Transactional
    private void getUserOrCreate(Long id) {
        log.info("{}: user ID {}.", getClass().getSimpleName(), id);
        if (userRepository.findById(id).isEmpty()) {
            TelegramUser user = new TelegramUser();
            user.setId(id);
            log.info("{}: user has been created.", getClass().getSimpleName());
            userRepository.save(user);
        }
    }

    private SendMessage buildMessage(Long id, String message) {
        SendMessage sendMessage = new SendMessage(); // Create a SendMessage object with mandatory fields
        sendMessage.setChatId(id.toString());
        sendMessage.setText(message);
        sendMessage.enableHtml(true);
        return sendMessage;
    }

    @SneakyThrows
    private void handleAddingTracking(Long id, String trackCode) {
        Matcher postMatcher = INTERNATIONAL_POST_STANDARD_TRACK_NUMBER.matcher(trackCode);
        Matcher samsungMatcher = SAMSUNG_ORDER_NUMBER.matcher(trackCode);
        Matcher qwintryMatcher = QWINTRY_TRACK_PATTERN.matcher(trackCode);
        Matcher cseMatcher = CSE_TRACK_PATTERN.matcher(trackCode);
        if (!postMatcher.matches() && !samsungMatcher.matches() && !qwintryMatcher.matches() && !cseMatcher.matches()) {
            execute(buildMessage(id, FAIL_CODE_ADDED));
            return;
        }
        userRepository.findById(id).ifPresent(user -> {
            List<TrackingCode> trackCodes = trackingRepository.findAllByTelegramUserId(id);
            TrackingCode trackingCode = new TrackingCode();
            trackingCode.setTelegramUser(user);
            trackingCode.setCode(trackCode);
            if (samsungMatcher.matches()) {
                trackingCode.setProvider(SAMSUNG);
            } else if (qwintryMatcher.matches()) {
                trackingCode.setProvider(QWINTRY);
            } else if (cseMatcher.matches()) {
                trackingCode.setProvider(CSE);
            }
            if (trackCodes == null) {
                trackCodes = new ArrayList<>();
            }
            trackCodes.add(trackingCode);
            user.setStatus(trackingCode.getProvider() != null ? AWAITING_FOR_INPUT : SELECTING_PROVIDER);
            userRepository.save(user);
            trackingRepository.save(trackingCode);
            sendUserAddingConfirmation(trackingCode);
            sendUserAddingConfirmation(trackingCode);
        });
    }

    @SneakyThrows
    private void sendUserAddingConfirmation(TrackingCode trackingCode) {
        execute(buildMessage(trackingCode.getTelegramUser().getId(), SUCCESSFULLY_ADDED));
        if (trackingCode.getProvider() == null) {
            sendAvailableProviders(trackingCode.getTelegramUser().getId());
        }
        if (trackingCode.getProvider() != null && REQUIRE_ADDITIONAL_INFO.contains(trackingCode.getProvider())) {
            execute(buildMessage(trackingCode.getTelegramUser().getId(), NEED_EMAIL));
        }
    }

    @SneakyThrows
    private void sendAvailableProviders(Long id) {
        execute(buildMessage(id, SELECT_TRACKING_PROVIDER));
        TrackingProvider[] values = TrackingProvider.values();
        StringBuilder message = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            message.append(values[i].getProvider())
                    .append("\n");
        }
        execute(buildMessage(id, message.toString()));
    }

}
