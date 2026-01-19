package uz.lee.anonymousbot.bot;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.lee.anonymousbot.service.MediaDownloadService;
import uz.lee.anonymousbot.service.MediaType;

@Component
public class MediaDownloadBot extends TelegramLongPollingBot {

    private final MediaDownloadService mediaDownloadService;
    private final String botUsername;
    private final long maxFileBytes;

    public MediaDownloadBot(
            MediaDownloadService mediaDownloadService,
            @Value("${telegram.bot.username}") String botUsername,
            @Value("${telegram.bot.token}") String botToken,
            @Value("${media.max-file-mb:49}") long maxFileMb) {
        super(botToken);
        this.mediaDownloadService = mediaDownloadService;
        this.botUsername = botUsername;
        this.maxFileBytes = maxFileMb * 1024 * 1024;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        String text = message.getText().trim();
        if (text.equalsIgnoreCase("/start")) {
            sendText(message, "Salom! URL yuboring. Masalan: \n" +
                    "video https://youtube.com/... \n" +
                    "audio https://tiktok.com/...");
            return;
        }

        Optional<MediaRequest> mediaRequest = parseRequest(text);
        if (mediaRequest.isEmpty()) {
            sendText(message, "Iltimos, URL yuboring yoki 'audio <url>' / 'video <url>' yozing.");
            return;
        }

        MediaRequest request = mediaRequest.get();
        sendText(message, "Yuklab olinmoqda, biroz kuting...");

        try {
            Path downloaded = mediaDownloadService.download(request.url(), request.type());
            File file = downloaded.toFile();
            if (!file.exists()) {
                sendText(message, "Fayl topilmadi. URL ni tekshiring.");
                return;
            }
            if (file.length() > maxFileBytes) {
                sendText(message, "Fayl hajmi katta. " + (maxFileBytes / 1024 / 1024) + "MB dan kichik fayl yuboring.");
                return;
            }
            sendMedia(message, request.type(), file);
        } catch (Exception e) {
            sendText(message, "Yuklashda xatolik: " + e.getMessage());
        }
    }

    private void sendMedia(Message message, MediaType type, File file) throws TelegramApiException {
        if (type == MediaType.AUDIO) {
            SendAudio sendAudio = new SendAudio();
            sendAudio.setChatId(message.getChatId());
            sendAudio.setAudio(new InputFile(file));
            execute(sendAudio);
            return;
        }

        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(message.getChatId());
        sendDocument.setDocument(new InputFile(file));
        execute(sendDocument);
    }

    private void sendText(Message message, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            // ignore
        }
    }

    private Optional<MediaRequest> parseRequest(String text) {
        String lowered = text.toLowerCase();
        MediaType type = MediaType.VIDEO;
        String url = text;
        if (lowered.startsWith("audio ")) {
            type = MediaType.AUDIO;
            url = text.substring(6).trim();
        } else if (lowered.startsWith("video ")) {
            type = MediaType.VIDEO;
            url = text.substring(6).trim();
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return Optional.of(new MediaRequest(type, url));
        }

        return Optional.empty();
    }

    private record MediaRequest(MediaType type, String url) {}
}
