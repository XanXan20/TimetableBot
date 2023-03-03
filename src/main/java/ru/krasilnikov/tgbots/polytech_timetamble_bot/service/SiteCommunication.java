package ru.krasilnikov.tgbots.polytech_timetamble_bot.service;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class SiteCommunication {
    public static String downloadFile() throws IOException{
        Document site = Jsoup.connect("https://polytech-rzn.ru/?page_id=14410").get();

        Element downloadButton = site.select("div.ramka:nth-child(7) > p:nth-child(1) > a:nth-child(8)").first();
        String url = downloadButton.attr("href");

        Element dateElement = site.select("div.ramka:nth-child(7) > p:nth-child(1) > font:nth-child(1)").first();
        TelegramBot.date = dateElement.text();

        String[] urlArray = url.split("/");
        String fileName = urlArray[urlArray.length-1];

        FileUtils.copyURLToFile(new URL(url), new File("/home/TimetableBot/data/timetables/" + fileName));
        System.out.println("Загружен файл по ссылке: " + url);
        return "/home/TimetableBot/data/timetables/" + fileName;
    }
}
