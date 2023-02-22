package ru.krasilnikov.tgbots.polytech_timetamble_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PolytechTimetambleBotApplication {

    public static void main(String[] args) {
        try{
            SpringApplication.run(PolytechTimetambleBotApplication.class, args);
            System.out.println("Проект запущен");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
