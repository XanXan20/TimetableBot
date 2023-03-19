package ru.krasilnikov.tgbots.polytech_timetamble_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.krasilnikov.tgbots.polytech_timetamble_bot.config.BotConfig;
import ru.krasilnikov.tgbots.polytech_timetamble_bot.excel.ConvertXlsxToXls;
import ru.krasilnikov.tgbots.polytech_timetamble_bot.excel.XLSFileReader;
import ru.krasilnikov.tgbots.polytech_timetamble_bot.model.User;
import ru.krasilnikov.tgbots.polytech_timetamble_bot.model.UserRepository;

import java.io.*;
import java.io.File;
import java.sql.Timestamp;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {
    @Value("${dataFolderPath}")
    String dataFolderPath;
    @Value("${logsFilePath}")
    String logsFilePath;
    String lastFilePath = "/home/TimetableBot/data/last_file.txt";
    @Value("${feedbackFilePath}")
    String feedbackFilePath;
    @Value("${zipLogsFolderPath}")
    String zipLogsFolderPath;
    XLSFileReader excelFileReader;
    static String date = "{тут должна быть дата}";
    String oldPath;//тут крч записывается старое расписание, и када новое приходит, он названия сравнивает
    @Autowired
    private UserRepository userRepository;
    final static String SPECIAL_THANKS =
            "Отдельная благодарность:\n"+
                    "@Bloods_4L\n"+
                    "@tiltmachinegun";
    final static String HELP_TXT =
            "Внимание! Если бот вдруг не работает как надо, то возможно вас еще нет в базе. Чтобы зарегистрироваться, отправьте команду /start\n"+
                    "Доступные команды:\n\n"+

                    "\t/help - список доступных команд\n" +
                    "\t/changegroup [номер_группы] (напр. /changegroup 71)- подписаться на уведомления по расписанию для вашей группы\n"+
                    "\t/mygroup - узнать свою текущую группу\n"+
                    "\t/timetable - узнать расписание своей группы\n"+
                    "\t/thanks - благодарности\n"+
                    "\t/update - проверить расписание на сайте. ВНИМАНИЕ функция экспериментальная, прошу использовать только если вы подписаны на обновления, а вам все еще не пришло расписание\n\n"+

                    "При обнаружении багов, или если есть какие-либо пожелания, то пишите мне в тг:\n"+
                    "@Sasalomka";

    Timer timer;

    final BotConfig config;
    public TelegramBot(BotConfig config){
        this.config = config;

        try{
            excelFileReader = new XLSFileReader();
            excelFileReader.update();

        }catch (Exception e){
            System.out.println(e.getMessage());
            checkNewTimetable();
        }

        timer = new Timer();
    }
    @Override
    public String getBotUsername() {
        return config.getBotName();
    }
    @Override
    public String getBotToken() {
        return config.getToken();
    }
    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText() && !update.getMessage().hasDocument()){

            String[] messageText = update.getMessage().getText().split(" ");
            long chatId = update.getMessage().getChatId();

            switch (messageText[0]){
                case "/start":
                    registerUser(update.getMessage());
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    break;
                case "/help":
                    helpCommandReceived(chatId);
                    break;
                case "/thanks":
                    thanksCommandReceiver(chatId);
                    break;
                case "/changegroup":
                    if(messageText.length == 2) {
                        String stringGroupId = messageText[1];

                        changeGroupCommandReceiver(update.getMessage(), stringGroupId);
                    }
                    else{
                        sendMessage(update.getMessage().getChatId(), "Используйте шаблон /changegroup [номер группы]");
                    }
                    break;
                case "/mygroup":
                    myGroupCommandReceiver(update.getMessage().getChatId());
                    break;
                case "/getUpd":
                    sendFile(update.getMessage().getChatId(), new File("/home/data-dumb.sql"));
                    break;
                case "/myrole":
                    myRoleCommandReceiver(update.getMessage().getChatId());
                    break;
                case "/timetable":
                    timetableCommandReceiver(chatId);
                    break;
                case "/autonotice":
                    autoNoticeCommandReceiver(chatId);
                    break;
                case "/update":
                    checkNewTimetable();
                    break;
                default:
                    sendMessage(chatId, "Простите, эта команда неправильна, или не поддерживается.");
            }
        }
    }
    private void registerUser(Message msg) {

        if(userRepository.findById(msg.getChatId()).isEmpty()){

            Long chatId = msg.getChatId();
            Chat chat = msg.getChat();

            User user = new User();

            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            user.setNotice(true);

            userRepository.save(user);
            logsUpdate(new Date() + "\tUser: " + chatId + " was registered");
        }
    }
    private void startCommandReceived(long chatId, String name){
        String answer = "Привет, " + name + ", будем знакомы!\n" +
                "Скоро ты сможешь смотреть расписание в нашем ЗАМЕЧАТЕЛЬНОМ КОЛЛЕДЖЕ\n\n" +
                "P.S. ОБЯЗАТЕЛЬНО отправь /help, чтобы узнать больше о боте";

        log.info("User " + name + " started work with bot");

        sendMessage(chatId, answer);
    }
    private void helpCommandReceived(long chatId){
        sendMessage(chatId, HELP_TXT);
        logsUpdate(new Date() + "\tUser: " + chatId + " HELP COMMAND");
    }
    private void myGroupCommandReceiver(long chatId){

        Optional<User> optionalUser = userRepository.findById(chatId);
        User user = optionalUser.get();

        if(user.getGroupId() == 0){
            sendMessage(chatId, "Вы еще не выбрали свою группуб используйте /changegroup [номер группы] для выбора своей группы");
        }
        else {
            sendMessage(chatId, "Номер вашей группы: " + user.getGroupId());
        }
        logsUpdate(new Date() + "\tUser: " + chatId + " MYGROUP COMMAND");
    }
    private void changeGroupCommandReceiver(Message msg, String stringGroupId){

        int intGroupId = 0;
        try {
            intGroupId = Integer.parseInt(stringGroupId);
        }catch (Exception e){
            log.error("Group Id not changed: exception");
            sendMessage(msg.getChatId(), "Ошибка в прочтении номера группы, возможно вы использовали буквы, а не числа, попробуйте еще раз.");
            return;
        }

        ArrayList<Integer> groupList = excelFileReader.getGroupIdList();

        for(int i : groupList){
            if(intGroupId == i){
                Optional<User> optionalUser = userRepository.findById(msg.getChatId());

                User user = optionalUser.get();
                int oldGroupId = user.getGroupId();
                user.setGroupId(intGroupId);

                userRepository.save(user);

                sendMessage(msg.getChatId(), "Ваша группа успешно изменена.");
                logsUpdate(new Date() + "\tUser: " + msg.getChatId() + " CHANGE GROUP FROM: " + oldGroupId + " TO: " + stringGroupId);
                return;
            }
        }
        sendMessage(msg.getChatId(), "Такой группы не найдено, проверьте корректность введенных данных");
    }
    private void myRoleCommandReceiver(long chatId){

        int role = getRole(chatId);

        String text = "Ваша роль: ";

        switch (role) {
            case 0:
                sendMessage(chatId, text + "бедолага");
                break;
            case 1:
                sendMessage(chatId, text + "работяга");
                break;
            case 2:
                sendMessage(chatId, text + "гигачад");
                break;
        }
        logsUpdate(new Date() + "\tUser: " + chatId + " MYROLE COMMAND");
    }
    private void timetableCommandReceiver(long chatId){

        Optional<User> optionalUser = userRepository.findById(chatId);
        User user = optionalUser.get();
        int userGroup = user.getGroupId();

        String answer = findGroupTimetable(userGroup);

        sendMessage(chatId, date + ":\n\n" + answer);
        sendFile(chatId, excelFileReader.getFile());

        logsUpdate(new Date() + "\tUser: " + chatId + " TIMETABLE COMMAND");
    }
    private void autoNoticeCommandReceiver(long chatId){

        Optional<User> optionalUser = userRepository.findById(chatId);
        User user = optionalUser.get();
        user.setNotice(!user.isNotice());

        if(user.isNotice()){
            sendMessage(chatId, "Вы подписались на уведомления");
        }else{
            sendMessage(chatId, "Вы отписались от уведомлений");
        }

        userRepository.save(user);

        logsUpdate(new Date() + "\tUser: " + chatId + " AUTONOTICE COMMAND; SUBSCRIBER = " + user.isNotice());
    }
    private void thanksCommandReceiver(long chatId){
        sendMessage(chatId, SPECIAL_THANKS);
        logsUpdate(new Date() + "\tUser: " + chatId + " THANKS COMMAND");
    }
    private void noticeCommandReceiver(){
        ArrayList<Integer> list = excelFileReader.getGroupIdList();
        Iterable<User> users = userRepository.findAll();

        for (Integer i:
             list) {

            String timetable = findGroupTimetable(i);
            for (User user : users) {
                if (user.getGroupId() == i && user.isNotice()) {

                    sendMessage(user.getChatId(), date + ":\n\n" + timetable +
                            "\n" +
                            "Не ваше расписание?\n" +
                            "/mygroup - ваша группа\n" +
                            "/changegroup [номер_группы] - выбрать вашу группу");
                    sendFile(user.getChatId(), excelFileReader.getFile());
                }
            }
        }

    }
    private void customNoticeCommandReceiver(ArrayList<String> messageList, boolean isOnlySub, long chatId){
        Iterable<User> users = userRepository.findAll();
        String message = "";

        for(String str : messageList){
            message += str + " ";
        }
        message = message.trim();
        if(isOnlySub) {
            for (User user :
                    users) {
                if (user.isNotice())
                    sendMessage(user.getChatId(), message);
            }
        }
        else{
            for (User user :
                    users) {
                sendMessage(user.getChatId(), message);
            }
        }

        logsUpdate(new Date() + "\tUser: " + chatId + " CUSTOMNOTICE COMMAND;\n\tNOTICE TEXT: " + message);
    }
    private void feedbackCommandReceiver(Message message, ArrayList<String> feedback){
        String feedbackFile = feedbackFilePath;
        try (FileWriter writer = new FileWriter(feedbackFile, true)){
            Date date = new Date();
            writer.write("Username: " + message.getChat().getUserName() + " ID: " + message.getChatId() + " Date: " + date + "--> ");
            for(String str : feedback){
                writer.write(str + " ");
            }
            writer.write("\n");
            writer.flush();
        }catch (IOException e){
            System.out.println(e.getMessage());
        }

        sendMessage(message.getChatId(), "Спасибо за фидбек, еще можно написать мне в тг (подробности в /help)");

        logsUpdate(new Date() + "\tUser: " + message.getChatId() + " FEEDBACK COMMAND");
    }
    private void personalNoticeCommandReceiver(String chatId, ArrayList<String> messageList){
        long l = Long.parseLong(chatId);
        String message = "";

        for(String str : messageList){
            message += str + " ";
        }
        message = message.trim();

        sendMessage(l, message);

        logsUpdate(new Date() + "\tUser: " + chatId + " SEND PERSONAL NOTICE TO USER: " + l + "\n\tMESSAGE TEXT: " + message);
    }
    private void sendMessage(long chatId, String textToSend){
        SendMessage message = new SendMessage();

        message.setChatId(chatId);
        message.setText(textToSend);

        try {
            execute(message);
        }catch(TelegramApiException e){
            log.error("Error occured: " + e.getMessage());
        }
    }
    private void sendFile(long chatId, java.io.File file){

        Long longChatId = chatId;

        InputFile inputFile = new InputFile(file);

        SendDocument sendDocument = new SendDocument(longChatId.toString(), inputFile);
        try{
            execute(sendDocument);
        }catch (TelegramApiException e){
            System.out.println(e.getMessage());
        }
    }
    private int getRole(long chatId){
        Optional<User> optionalUser = userRepository.findById(chatId);
        User user = optionalUser.get();

        return user.getRole();
    }
    private String findGroupTimetable(int groupId){

        Map<Integer, String> groupTimetable = excelFileReader.getGroupTimetable(groupId);
        String answer = "";

        for (int i = 1; i < 15; i++) {

            if(groupTimetable.get(i) == null){
                continue;
            }

            String[] lesionName = groupTimetable.get(i).split(",");

            answer += i + " - ";
            for(String str : lesionName){
                str = str.trim();
                str = str.replaceAll("\\s+", " ");
                answer += str + " | ";
            }
            answer += "\n";
        }
        return answer;
    }

    private void setLastFile(String fileName){
        try(FileWriter writer = new FileWriter(lastFilePath)) {
            writer.write(fileName);
        }catch (IOException e){
            System.out.println(e.getMessage());
        }
    }
    private void logsUpdate(String log){
        try(FileWriter writer = new FileWriter(logsFilePath, true);){

            writer.write(log + "\n");

            java.io.File file = new java.io.File(logsFilePath);

            long fileInBytes = file.length();
            long fileInKb = fileInBytes/1024;
            long fileInMb = fileInKb/1024;

            if(fileInMb > 100){
                System.out.println("Должна начаться архивация файла");
                try(ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipLogsFolderPath + new Date() + ".zip"));
                    FileInputStream fis = new FileInputStream(logsFilePath)){

                    ZipEntry zipEntry = new ZipEntry(new Date().toString());
                    zos.putNextEntry(zipEntry);

                    byte[] buf = new byte[fis.available()];
                    fis.read(buf);

                    zos.write(buf);
                    zos.closeEntry();

                    file.delete();
                }
            }

        }catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    private void checkNewTimetable(){
        sendMessage(634876835, "Проверка обновлений на сайте, пожалуйста подождите...");

        try{
            String path = SiteCommunication.downloadFile();
            if(!path.equals(oldPath)) {
                timer.cancel();
                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        checkNewTimetable();
                    }
                }, 32000000, 7200000);
                oldPath = path;

                ConvertXlsxToXls.convert(path);
                System.out.println("Файл загружен");
                excelFileReader.update();
                noticeCommandReceiver();
            }

            sendMessage(634876835, "Расписание обновлено");
        }catch (Exception e){
            e.printStackTrace();
        }
        //logsUpdate(new Date() + "\tUser: " + 634876835 + " UPDATE COMMAND");
    }
}
