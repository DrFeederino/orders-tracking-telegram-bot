package com.drfeederino.telegramwebchecker.services;

import com.drfeederino.telegramwebchecker.entities.TelegramUser;
import com.drfeederino.telegramwebchecker.entities.TrackingCode;
import com.drfeederino.telegramwebchecker.enums.BotState;
import com.drfeederino.telegramwebchecker.enums.TrackingProvider;
import com.drfeederino.telegramwebchecker.enums.UserStatus;
import com.drfeederino.telegramwebchecker.pojo.RowData;
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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.drfeederino.telegramwebchecker.constants.BotMessages.*;
import static com.drfeederino.telegramwebchecker.constants.Patterns.*;
import static com.drfeederino.telegramwebchecker.enums.TrackingProvider.*;
import static com.drfeederino.telegramwebchecker.enums.UserStatus.*;

@Service
@Slf4j
public class WebCheckerBot extends TelegramLongPollingBot {

    private static final Set<TrackingProvider> REQUIRE_ADDITIONAL_INFO = Set.of(SAMSUNG);

    private final TrackingCodeRepository trackingRepository;
    private final TelegramUserRepository userRepository;

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
        if (update.hasMessage() && BotState.contains(update.getMessage().getText())) {
            handleUserState(update);
        } else if (update.hasMessage()) {
            handleUserMessageForStatus(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        } else {
            execute(buildMessage(update.getMessage().getChatId(), USE_COMMANDS_DUMMY));
        }
    }

    private void handleCallbackQuery(Update update) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        List<String> values = List.of(callbackQuery.getData().split("/"));
        UserStatus status = UserStatus.getEnum(values.get(0));
        switch(status) {
            case SELECTING_PROVIDER:
                setTrackingProvider(callbackQuery.getMessage().getChatId(), values.get(1), values.get(2));
                break;
        }
    }

    private void setTrackingProvider(Long chatId, String trackingId, String provider) {
        Optional<TrackingCode> code = trackingRepository.findById(Long.parseLong(trackingId));
        code.ifPresent(trackingCode -> {
            trackingCode.setProvider(TrackingProvider.getEnum(provider));
            trackingRepository.save(trackingCode);
            try {

                execute(buildMessage(chatId, DONE_DID_IT));
            } catch (TelegramApiException e) {
            }
        });
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
            execute(buildMessage(update.getMessage().getChatId(), USE_COMMANDS_DUMMY));
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
        execute(buildMessage(chatId, DONE_DID_IT));
    }

    @SneakyThrows
    private void handleDeleteTracking(Long chatId, String message) {
        if (message == null) {
            return;
        }
        if (message.contains("all")) {
            trackingRepository.deleteAllByTelegramUserId(chatId);
            execute(buildMessage(chatId, DONE_DID_IT));
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
                            execute(buildMessage(chatId, DONE_DID_IT));
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
            execute(buildMessage(chatId, DONE_DID_IT));
        } else {
            //execute(buildMessage(chatId, I_DONT_SPEAK_THIS_PROVIDER));
            //sendAvailableProviders(chatId, );
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
                execute(buildMessage(message.getChatId(), String.format(HIYA_THERE, update.getMessage().getChat().getUserName())));
                break;
            }
            case ADD: {
                execute(buildMessage(message.getChatId(), ADD_TRACK_CODE));
                updateAddTrackCode(message.getChatId());
                break;
            }
            case CHANGE: {
                updateTrackingCodes(message.getChatId());
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
                execute(buildMessage(message.getChatId(), WHAT_TRACK_CODE_DELETE));
                deleteTracking(message.getChatId());
                break;
            }
        }
    }

    @SneakyThrows
    private void updateTrackingCodes(Long chatId) {
        List<TrackingCode> userCodes = trackingRepository.findAllByTelegramUserId(chatId);
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        var buttons = userCodes.stream().map(code -> {
                    List<InlineKeyboardButton> row = new ArrayList<>();
                    InlineKeyboardButton button = new InlineKeyboardButton();
                    button.setText(code.getCode());
                    button.setCallbackData(code.getId() + "");
                    row.add(button);
                    return row;
                })
                .collect(Collectors.toList());
        keyboard.setKeyboard(buttons);
        SendMessage response = new SendMessage();
        response.setChatId(chatId + "");
        response.setText(SELECT_TO_CHANGE);
        response.setReplyMarkup(keyboard);
        execute(response);
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
        execute(buildMessage(chatId, DELETE_ALL_OPTION));
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
                            LATEST_STATUS_FORMAT,
                            trackingCode.getCode(),
                            trackingCode.getProvider() == null ? "????" : trackingCode.getProvider().getProvider(),
                            lastStatus != null ? lastStatus : UNKNOWN_STATUS
                    )
            ));
        }
        execute(buildMessage(chatId, UNKNOWN_TIP));
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

    private SendMessage buildMessage(Long id, String text, List<RowData> values) {
        SendMessage sendMessage = new SendMessage(); // Create a SendMessage object with mandatory fields
        sendMessage.setChatId(id.toString());
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (RowData data : values) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(data.getValue());
            button.setCallbackData(data.getCallbackData());
            row.add(button);
            rows.add(row);
        }
        keyboard.setKeyboard(rows);
        sendMessage.setReplyMarkup(keyboard);
        sendMessage.setText(text);
        return sendMessage;
    }

    @SneakyThrows
    private void handleAddingTracking(Long id, String trackCode) {
        Matcher postMatcher = INTERNATIONAL_POST_STANDARD_TRACK_NUMBER.matcher(trackCode);
        Matcher samsungMatcher = SAMSUNG_ORDER_NUMBER.matcher(trackCode);
        Matcher qwintryMatcher = QWINTRY_TRACK_PATTERN.matcher(trackCode);
        Matcher cseMatcher = CSE_TRACK_PATTERN.matcher(trackCode);
        if (!postMatcher.matches() && !samsungMatcher.matches() && !qwintryMatcher.matches() && !cseMatcher.matches()) {
            execute(buildMessage(id, TRACK_CODE_NOT_SUPPORTED));
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
        });
    }

    @SneakyThrows
    private void sendUserAddingConfirmation(TrackingCode trackingCode) {
        execute(buildMessage(trackingCode.getTelegramUser().getId(), DONE_DID_IT));
        if (trackingCode.getProvider() == null) {
            sendAvailableProviders(trackingCode.getTelegramUser().getId(), trackingCode.getId());
        }
        if (trackingCode.getProvider() != null && REQUIRE_ADDITIONAL_INFO.contains(trackingCode.getProvider())) {
            execute(buildMessage(trackingCode.getTelegramUser().getId(), NEED_EMAIL));
        }
    }

    @SneakyThrows
    private void sendAvailableProviders(Long userId, Long codeId) {
        TrackingProvider[] values = TrackingProvider.values();

        List<RowData> providers = Arrays.stream(values).map(code -> RowData.builder()
                        .value(code.getProvider())
                        .callbackData(String.format("%s/%s/%s", SELECTING_PROVIDER, codeId, code.getProvider()))
                        .build())
                .collect(Collectors.toList());

        execute(buildMessage(userId, WHAT_TRACKING_PROVIDER, providers));
    }

}
