package org.example.kino1409_bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InstagramVideoDownloaderAndSender extends TelegramLongPollingBot {

    private static final String BOT_TOKEN = "7614664649:AAGENEpOSpDTpdU6Nfy5pxtIjBbUEVHzaLw";
    private static final String BOT_USERNAME = "https://t.me/Ajal1409_bot";

    private final UserRepositary userRepositary;
    private final MovieRepositary movieRepositary;
    private final AdminRepositary adminRepositary;

    private final Map<Long, String> currentStep = new HashMap<>();
    private final Map<Long, String> newAdminName = new HashMap<>();
    private final Map<Long, Movie> newMovieData = new HashMap<>();

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            User user = new User();
            user.setChatId(update.getMessage().getChatId());
            user.setName(update.getMessage().getFrom().getFirstName());
            user.setUsername(update.getMessage().getFrom().getUserName());
            userRepositary.save(user);

            if (adminRepositary.findById(String.valueOf(chatId)).isPresent()) {
                try {
                    handleAdminCommands(update, chatId, messageText);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else {
                handleUserCommands(update, chatId, messageText);
            }
        } else if (update.hasMessage() && update.getMessage().hasVideo()) {
            handleMovieVideo(update);
        }
    }

    private void handleAdminCommands(Update update, Long chatId, String messageText) throws TelegramApiException {
        if (messageText.equals("/start")) {
            sendReplyKeyboardMessage(chatId);
        } else if (messageText.equals("Admin Qo'shish")) {
            sendTextMessage(chatId, "Iltimos, yangi adminning ismini kiriting:");
            currentStep.put(chatId, "WAITING_FOR_NAME");
        } else if (messageText.equals("Botdagi barcha foydalanuvchilarni ko'rish")) {
            String userListMarkdown = getUserList(userRepositary.findAll());
            sendTextMessage(chatId, userListMarkdown);
        } else if (messageText.equals("Filmni kod bo'yicha o'chirish")) {
            sendTextMessage(chatId, "Iltimos, o'chirmoqchi bo'lgan kino kodini kiriting:");
            currentStep.put(chatId, "WAITING_FOR_DELETE");
        } else if (messageText.equals("Kino qo'shish")) {
            sendTextMessage(chatId, "Iltimos, yangi kino faylini yuboring:");
            currentStep.put(chatId, "WAITING_FOR_VIDEO");
        } else if (messageText.equals("Kinolarni ko'rish")) {
            sendTextMessage(chatId, "Iltimos, kino kodini kiriting:");
            currentStep.put(chatId, "WAITING_FOR_MOVIE_CODE");
        } else if (messageText.equals("Userlarga xabar yuborish")) {
            sendTextMessage(chatId, "Iltimos, foydalanuvchilarga yuboriladigan xabarni yozing (matn, rasm yoki video URL):");
            currentStep.put(chatId, "WAITING_FOR_BROADCAST_MESSAGE");
        } else if (currentStep.getOrDefault(chatId, "").equals("WAITING_FOR_BROADCAST_MESSAGE")) {
            broadcastMessageToUsers(messageText);
            sendTextMessage(chatId, "Xabaringiz barcha foydalanuvchilarga yuborildi.");
            currentStep.remove(chatId);
        } else if (isNumeric(messageText) && currentStep.getOrDefault(chatId, "").equals("WAITING_FOR_DELETE")) {
            deleteMovieByCode(chatId, Integer.parseInt(messageText));
        } else if (isNumeric(messageText) && currentStep.getOrDefault(chatId, "").equals("WAITING_FOR_MOVIE_CODE")) {
            sendVideoByCode(chatId, Integer.parseInt(messageText));
        } else if (currentStep.getOrDefault(chatId, "").equals("WAITING_FOR_NAME")) {
            newAdminName.put(chatId, messageText);
            sendTextMessage(chatId, "Endi yangi adminning username'ini kiriting:");
            currentStep.put(chatId, "WAITING_FOR_USERNAME");
        } else if (currentStep.getOrDefault(chatId, "").equals("WAITING_FOR_USERNAME")) {
            String adminName = newAdminName.get(chatId);
            String adminUsername = messageText;
            saveNewAdmin(adminName, adminUsername);
            sendTextMessage(chatId, "Yangi admin muvaffaqiyatli qo'shildi:\nIsm: " + adminName + "\nUsername: @" + adminUsername);
            currentStep.remove(chatId);
            newAdminName.remove(chatId);
        }
    }

    private void handleUserCommands(Update update, Long chatId, String messageText) {
        if (messageText.equals("/start")) {
            sendReplyUserKeyboardMessage(chatId);
            sendTextMessage(chatId, "Iltimos, kino kodini kiriting:");
        } else if (isNumeric(messageText)) {
            sendVideoByCode(chatId, Integer.parseInt(messageText));
        } else if (messageText.equals("Admin bilan bog'lanish")) {
            sendTextMessage(chatId, "Iltimos, xabaringizni kiriting:");
            currentStep.put(chatId, "WAITING_FOR_Admins");
        } else if (currentStep.getOrDefault(chatId, "").equals("WAITING_FOR_Admins")) {
            sendAdminMessage(update.getMessage().getText(), chatId);
        } else {
            sendTextMessage(chatId, "Iltimos, kodni faqat raqamlar orqali kiriting!");
        }
    }

    private void handleMovieVideo(Update update) {
        Long chatId = update.getMessage().getChatId();
        if (currentStep.getOrDefault(chatId, "").equals("WAITING_FOR_VIDEO")) {
            String fileId = update.getMessage().getVideo().getFileId();
            Movie firstByOrderByCodeDesc = movieRepositary.findFirstByOrderByCodeDesc();
            Movie put;
            if (firstByOrderByCodeDesc == null) {
                put = new Movie(fileId, 1, update.getMessage().getCaption(), LocalDate.now());
            } else {
                put = new Movie(fileId, firstByOrderByCodeDesc.getCode() + 1, update.getMessage().getCaption(), LocalDate.now());
            }

            movieRepositary.save(put);
            sendTextMessage(update.getMessage().getChatId(), "Kino muvaffaqiyatli saqlandi");
            currentStep.put(chatId, "WAITING_FOR_CODE");
        }
    }

    private void sendReplyUserKeyboardMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Botga Xush Kelibsiz");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Admin bilan bog'lanish"));

        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendReplyKeyboardMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Admin qo'shish, kino o'chirish, kino qo'shish va ko'rish bo'yicha tanlovni tanlang.");

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Admin Qo'shish"));
        row1.add(new KeyboardButton("Filmni kod bo'yicha o'chirish"));
        row1.add(new KeyboardButton("Botdagi barcha foydalanuvchilarni ko'rish"));
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Kino qo'shish"));
        row2.add(new KeyboardButton("Kinolarni ko'rish"));
        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("Userlarga xabar yuborish"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        keyboardMarkup.setKeyboard(keyboard);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendVideoByCode(Long chatId, int code) {
        Movie movie = movieRepositary.findByCode(code);
        if (movie != null) {
            SendVideo sendVideo = new SendVideo();
            sendVideo.setChatId(chatId.toString());
            sendVideo.setVideo(new InputFile(movie.getFile_id()));
            sendVideo.setCaption("Kino kodi: " + movie.getCode() + "\n" + movie.getCaption());
            try {
                execute(sendVideo);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        } else {
            sendTextMessage(chatId, "Bu kod bo'yicha kino topilmadi.");
        }
    }

    private void deleteMovieByCode(Long chatId, int code) {
        Movie movie = movieRepositary.findByCode(code);
        if (movie != null) {
            movieRepositary.delete(movie);
            sendTextMessage(chatId, "Kino muvaffaqiyatli o'chirildi.");
        } else {
            sendTextMessage(chatId, "Bu kod bo'yicha kino topilmadi.");
        }
    }

    private String getUserList(List<User> users) {
        StringBuilder sb = new StringBuilder();
        sb.append("Foydalanuvchilar ro'yxati:\n");
        for (User user : users) {
            sb.append("- ").append(user.getName()).append(" (@").append(user.getUsername()).append(")\n");
        }
        return sb.toString();
    }

    private void broadcastMessageToUsers(String messageText) {
        List<User> users = userRepositary.findAll();
        for (User user : users) {
            SendMessage message = new SendMessage();
            message.setChatId(user.getChatId().toString());
            message.setText(messageText);
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveNewAdmin(String name, String username) {
        Admin admin = new Admin();
        admin.setName(name);
        admin.setUsername(username);
        adminRepositary.save(admin);
    }

    private void sendAdminMessage(String text, Long userChatId) {
        List<Admin> admins = adminRepositary.findAll();
        for (Admin admin : admins) {
            SendMessage message = new SendMessage();
            message.setChatId(admin.getId());
            message.setText("Foydalanuvchidan yangi xabar:\n\n" + text + "\n\nChat ID: " + userChatId);
            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}


