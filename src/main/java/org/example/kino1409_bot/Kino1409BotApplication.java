package org.example.kino1409_bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@SpringBootApplication
public class Kino1409BotApplication {

    public static void main(String[] args) {
        SpringApplication.run(Kino1409BotApplication.class, args);
    }

    @Autowired
    private MovieRepositary movieRepositary;
    @Autowired
    private AdminRepositary adminRepositary;
    @Autowired
    private UserRepositary userRepositary;

    @Bean
    public TelegramBotsApi telegramBotsApi() throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

        InstagramVideoDownloaderAndSender bot = new InstagramVideoDownloaderAndSender(userRepositary,movieRepositary,adminRepositary);
        botsApi.registerBot(bot);

        return botsApi;
    }
}
