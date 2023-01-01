package com.example.backtestcbt3.controller;


import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.login.LoginException;
import javax.transaction.Transactional;
import javax.websocket.server.PathParam;


import com.example.backtestcbt3.model.Candlestick;
import com.example.backtestcbt3.model.FakeOrder;
import com.example.backtestcbt3.model.User;
import com.example.backtestcbt3.repository.CandlestickRepository;
import com.example.backtestcbt3.repository.FakeOrderRepository;
import com.example.backtestcbt3.repository.UserRepository;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Controller
public class UserController {
    @Autowired
    CandlestickRepository candlestickRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    FakeOrderRepository fakeOrderRepository;

    private static CandlestickRepository staticCandlestickRepository;
    private static UserRepository staticUserRepository;
    private static FakeOrderRepository staticFakeOrderRepository;

    @GetMapping("/load-data")
    public String loadData(){
        removeFakeOrders();
        removeCandlesticksData();
        saveCandlesticksData();
        return "visualiser";
    }
    @GetMapping("/backtest-data/{stepBack}/{takeProfit}")
    public String backtestData(@PathVariable int stepBack, @PathVariable double takeProfit, Model model) throws FileNotFoundException, ParseException, InterruptedException {
        removeFakeOrders();
        strategyTester(stepBack, takeProfit);
        visualiseDataModel(model);
        return "visualiser";
    }
    @GetMapping("/visualise-data")
    public String visualiseData(Model model){
        visualiseDataModel(model);
        return "visualiser";
    }
    public void visualiseDataModel(Model model){
        ArrayList<Double> dataTotal = new ArrayList<>();
        ArrayList<Double> dataLong = new ArrayList<>();
        ArrayList<Double> dataShort = new ArrayList<>();
        ArrayList<String> dateTotal = new ArrayList<>();
        ArrayList<String> dateLong = new ArrayList<>();
        ArrayList<String> dateShort = new ArrayList<>();
        double startingBalanceTotal = 1000;
        double startingBalanceLong = 1000;
        double startingBalanceShort = 1000;
        dataTotal.add(startingBalanceTotal);
        dateTotal.add("2022-12-01-00:00");

        dataLong.add(startingBalanceLong);
        dateLong.add("2022-12-01-00:00");

        dataShort.add(startingBalanceShort);
        dateShort.add("2022-12-01-00:00");
        ArrayList<FakeOrder> fakeLongOrders = staticFakeOrderRepository.findAllBySideAndExitPriceIsGreaterThan("Buy", 0);
        ArrayList<FakeOrder> fakeShortOrders = staticFakeOrderRepository.findAllBySideAndExitPriceIsGreaterThan("Sell", 0);
        ArrayList<FakeOrder> fakeTotalOrders = staticFakeOrderRepository.findAllByExitPriceIsGreaterThan(0);
        ArrayList<Candlestick> candlesticks = staticCandlestickRepository.findAll();
        for(int i = 0; i<fakeLongOrders.size();i++){
            startingBalanceLong += fakeLongOrders.get(i).getPnl();
            dataLong.add(startingBalanceLong);
            dateLong.add(fakeLongOrders.get(i).getTimeOpened());
        }
        for(int i = 0; i<fakeShortOrders.size();i++){
            startingBalanceShort += fakeShortOrders.get(i).getPnl();
            dataShort.add(startingBalanceShort);
            dateShort.add(fakeShortOrders.get(i).getTimeOpened());
        }

        for(int i = 0; i<fakeTotalOrders.size();i++){
            startingBalanceTotal += fakeTotalOrders.get(i).getPnl();
            dataTotal.add(startingBalanceTotal);
            dateTotal.add(fakeTotalOrders.get(i).getTimeOpened());
        }
        model.addAttribute("dataLong", dataLong.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dataShort", dataShort.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dataTotal", dataTotal.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dateTotal", dateTotal.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dateLong", dateLong.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dateShort", dateShort.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("candlesticks", makeHigherTimeframeCandlestick(candlesticks, 20));
    }
    public ArrayList makeHigherTimeframeCandlestick(ArrayList<Candlestick> candlesticks, int size){
        ArrayList<Candlestick> candlesticksHigher = new ArrayList<>();
        int count = 0;
        Candlestick candlestick = new Candlestick();
        for(int i = 0; i<candlesticks.size();i++){
            if(count==size){
                candlesticksHigher.add(candlestick);
                candlestick = null;
                count = 0;
                continue;
            }
            if(count==0){
                candlestick = candlesticks.get(i);
            }
            else if(candlesticks.get(i).getHigh()>candlestick.getHigh()){
                candlestick.setHigh(candlesticks.get(i).getHigh());
            }
            else if(candlesticks.get(i).getLow()<candlestick.getLow()){
                candlestick.setLow(candlesticks.get(i).getLow());
            }
            count++;
        }
        return candlesticksHigher;
    }
    public void strategyTester(int stepBack, double takeProfit) throws InterruptedException, FileNotFoundException, ParseException {

        //The daily high and low. Symbolizes liquidity as a primary-key in the strategy
        Candlestick dailyHigh = null;
        Candlestick dailyLow = null;

        double startingBalanceLong = 5000;
        double startingBalancShort = 5000;

        //Whole dataset
        ArrayList<Candlestick> candlesticks = staticCandlestickRepository.findAll();
        ArrayList<Candlestick> simulatedCandlesticks = new ArrayList<>();
        for(int i = 0; i<candlesticks.size();i++){
            //Adding a new candlestick like below simulates realtime data with a new opne, close low, high
            simulatedCandlesticks.add(candlesticks.get(i));

            //Removes old values in the simulated data not being used for iteration efficiency
            simulatedCandlesticks = removeOverflow(simulatedCandlesticks, stepBack);

            //Check if a long position is open
            startingBalanceLong = monitorLongPositions(simulatedCandlesticks.get(simulatedCandlesticks.size()-1), startingBalanceLong);
            //Check if a short position is open
            startingBalancShort = monitorShortPositions(simulatedCandlesticks.get(simulatedCandlesticks.size()-1), startingBalancShort);

            //Check if the data is old enough to start working with. Ex 1440 on the minute chart equals 24 hours of data.
            if(simulatedCandlesticks.size()>stepBack-1){
                double volume = dailyHigh.getHigh() / dailyLow.getLow();
                //If the algorithm is valid for short, a short will open based upon the function 'priceBounceBearish'
                if(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOfiBearish()>0.95 && simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose()<dailyHigh.getClose() && volume>1.012/* && simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose()<simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getEma()*/){
                    if(isCurrentYoungerThan4Ticks(simulatedCandlesticks.get(simulatedCandlesticks.size()-1), dailyHigh, 5)){
                        createFakeShort(simulatedCandlesticks, takeProfit, atr(simulatedCandlesticks));
                    }
                }
                //If the algorithm is valid for long, a long will open based upon the function 'priceBounceBullish'
                if(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOfiBullish()>0.95 && simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose()>dailyLow.getClose() && volume>1.012/* && simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose()>simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getEma()*/){
                    if(isCurrentYoungerThan4Ticks(simulatedCandlesticks.get(simulatedCandlesticks.size()-1), dailyLow, 5)){
                        createFakeLong(simulatedCandlesticks, takeProfit, atr(simulatedCandlesticks));
                    }
                }
            }
            dailyHigh = getDailyHigh(simulatedCandlesticks);
            dailyLow = getDailyLow(simulatedCandlesticks);
        }
        //Print the results. Static variables are being used to compare best previous iterations of params
        returnResults();
    }
    public boolean shortCloseValidation(ArrayList<Candlestick> candlesticks, Candlestick dailyHigh){
        for (int i = candlesticks.size()-1; i >= 0; i--) {
            if(candlesticks.get(i).getHigh()>dailyHigh.getHigh()){
                return false;
            }
        }
        return true;
    }
    public boolean longCloseValidation(ArrayList<Candlestick> candlesticks, Candlestick dailyLow){
        for (int i = candlesticks.size()-1; i >= 0; i--) {
            if(candlesticks.get(i).getLow()<dailyLow.getLow()){
                return false;
            }
        }
        return true;
    }
    public double atr(ArrayList<Candlestick> candlesticks){
        ArrayList<Double> trueRange = new ArrayList<>();
        for(int i = candlesticks.size()-14; i<candlesticks.size();i++){
            double HL = candlesticks.get(i).getHigh()-candlesticks.get(i).getLow();
            double HPC = candlesticks.get(i).getHigh()-candlesticks.get(i-1).getClose();
            double LPC = candlesticks.get(i).getLow()-candlesticks.get(i-1).getClose();
            if(HL >= HPC && HL >= LPC){
                trueRange.add(HL);
            }
            else if(HPC >= HL && HPC >= LPC){
                trueRange.add(HPC);
            }
            else if(LPC >= HL && LPC >= HPC){
                trueRange.add(LPC);
            }
        }
        double tr = 0;
        for(int i = 0; i<trueRange.size();i++){
            tr+=trueRange.get(i);
        }
        return tr/trueRange.size();
    }
    public static void returnResults(){
        double profits = 0;
        double wins = 0;
        double losses = 0;

        double biggestWin = 1;
        double biggestLoss = 1;
        ArrayList<FakeOrder> fakeOrders = staticFakeOrderRepository.findAllByExitPriceIsGreaterThan(0);
        for(int i = 0; i<fakeOrders.size();i++){
            //System.out.println(completedOrders.get(i).getPnl() + " = " + (profits + completedOrders.get(i).getPnl()) + " roi: " + completedOrders.get(i).getRoi());
            profits += fakeOrders.get(i).getPnl();
            //System.out.println(completedOrders.get(i).getPnl() + " : " + (completedOrders.get(i).getRoi()-1)*100);
            if(fakeOrders.get(i).getPnl()>0){
                wins++;
            }
            else{
                losses++;
            }
            if(fakeOrders.get(i).getRoi()>biggestWin){
                biggestWin = fakeOrders.get(i).getRoi();
            }
            else if(fakeOrders.get(i).getRoi()<biggestLoss){
                biggestLoss = fakeOrders.get(i).getRoi();
            }
        }
        System.out.println("time period        : " + fakeOrders.get(0).getTimeOpened() + " to " + fakeOrders.get(fakeOrders.size()-1).getTimeOpened());
        System.out.println("starting balance   : " + 1000 + " $");
        System.out.println("profit             : " + (int)profits + " $");
        System.out.println("wins               : " + (wins));
        System.out.println("losses             : " + (losses));
        System.out.println("biggest win        : " + ((biggestWin)-1)*100 + " %");
        System.out.println("biggest loss       : " + ((biggestLoss)-1)*100 + " %");
        System.out.println("wirate             : " + (wins/(wins+losses))*100 + " %");
        System.out.println("ROI                : " + ((profits)/1000)*100 + " %");
        System.out.println("---");

    }
    public static void createFakeShort(ArrayList<Candlestick> simulatedCandlesticks, double takeProfit, double atr){
        FakeOrder fakeOrder = new FakeOrder();
        fakeOrder.setSide("Sell");
        fakeOrder.setTimeOpened(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOpenTime());
        fakeOrder.setEntryPrice(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose());
        fakeOrder.setStopLoss(getDailyHigh(simulatedCandlesticks).getHigh());
        fakeOrder.setTakeProfit(calcTPShort(fakeOrder.getStopLoss(), fakeOrder.getEntryPrice(), takeProfit));
        if(fakeOrder.getTakeProfit()<(fakeOrder.getEntryPrice()*0.998)){
            staticFakeOrderRepository.save(fakeOrder);
        }
    }
    public static void createFakeLong(ArrayList<Candlestick> simulatedCandlesticks, double takeProfit, double atr){
        FakeOrder fakeOrder = new FakeOrder();
        fakeOrder.setSide("Buy");
        fakeOrder.setTimeOpened(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOpenTime());
        fakeOrder.setEntryPrice(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose());
        fakeOrder.setStopLoss(getDailyLow(simulatedCandlesticks).getLow());
        fakeOrder.setTakeProfit(calcTPLong(fakeOrder.getStopLoss(), fakeOrder.getEntryPrice(), takeProfit));
        if(fakeOrder.getTakeProfit()>(fakeOrder.getEntryPrice()*1.002)){
            staticFakeOrderRepository.save(fakeOrder);
        }
    }
    public static double calcTPLong(double stopLoss, double entryPrice, double riskReward){
        double diff = entryPrice - stopLoss;
        double tp = ((diff/entryPrice)*riskReward)+1;
        tp= tp*entryPrice;
        return tp;
    }
    public static double calcTPShort(double stopLoss, double entryPrice, double riskReward){
        double diff = entryPrice - stopLoss;
        double tp = ((diff/entryPrice)*riskReward)+1;
        tp= tp*entryPrice;
        return tp;
    }
    public static double monitorShortPositions(Candlestick candlestick, double startingBalance) throws InterruptedException {
        ArrayList<FakeOrder> fakeShortOrders = staticFakeOrderRepository.findAllBySideAndExitPrice("Sell", 0);
        for(int i = 0; i<fakeShortOrders.size();i++) {
            if (candlestick.getLow() < fakeShortOrders.get(i).getTakeProfit() && candlestick.getHigh() < fakeShortOrders.get(i).getStopLoss()) {
                fakeShortOrders.get(i).setExitPrice(candlestick.getClose());
                fakeShortOrders.get(i).setRoi(fakeShortOrders.get(i).getEntryPrice() / fakeShortOrders.get(i).getExitPrice());
                fakeShortOrders.get(i).setRoi(fakeShortOrders.get(i).getRoi() * 0.9998);
                fakeShortOrders.get(i).setPnl(startingBalance * fakeShortOrders.get(i).getRoi());
                fakeShortOrders.get(i).setPnl(fakeShortOrders.get(i).getPnl() - startingBalance);
                fakeShortOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                startingBalance+=fakeShortOrders.get(i).getPnl();
                System.out.println("short $$$ : roi: " + fakeShortOrders.get(i).getRoi() + " : pnl: " + fakeShortOrders.get(i).getPnl() + " : " + fakeShortOrders.get(i).getTimeOpened());
                staticFakeOrderRepository.save(fakeShortOrders.get(i));
            } else if (candlestick.getHigh() > fakeShortOrders.get(i).getStopLoss()) {
                fakeShortOrders.get(i).setExitPrice(candlestick.getClose());
                fakeShortOrders.get(i).setRoi(fakeShortOrders.get(i).getEntryPrice() / fakeShortOrders.get(i).getExitPrice());
                fakeShortOrders.get(i).setRoi(fakeShortOrders.get(i).getRoi() * 0.9998);
                fakeShortOrders.get(i).setPnl(startingBalance * fakeShortOrders.get(i).getRoi());
                fakeShortOrders.get(i).setPnl(fakeShortOrders.get(i).getPnl() - startingBalance);
                fakeShortOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                startingBalance+=fakeShortOrders.get(i).getPnl();
                System.out.println("short loss : roi: " + fakeShortOrders.get(i).getRoi() + " : pnl: " + fakeShortOrders.get(i).getPnl() + " : " + fakeShortOrders.get(i).getTimeOpened());
                staticFakeOrderRepository.save(fakeShortOrders.get(i));
            }
        }
        return startingBalance;
    }
    public static double monitorLongPositions(Candlestick candlestick, double startingBalance) throws InterruptedException {
        ArrayList<FakeOrder> fakeLongOrders = staticFakeOrderRepository.findAllBySideAndExitPrice("Buy", 0);
        for(int i = 0; i<fakeLongOrders.size();i++){
            if(candlestick.getHigh()>fakeLongOrders.get(i).getTakeProfit() && candlestick.getLow() > fakeLongOrders.get(i).getStopLoss()){
                fakeLongOrders.get(i).setExitPrice(candlestick.getClose());
                fakeLongOrders.get(i).setRoi(fakeLongOrders.get(i).getExitPrice() / fakeLongOrders.get(i).getEntryPrice());
                fakeLongOrders.get(i).setRoi(fakeLongOrders.get(i).getRoi()*0.9998);
                fakeLongOrders.get(i).setPnl(startingBalance*fakeLongOrders.get(i).getRoi());
                fakeLongOrders.get(i).setPnl(fakeLongOrders.get(i).getPnl()-startingBalance);
                fakeLongOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                startingBalance+=fakeLongOrders.get(i).getPnl();
                System.out.println("long $$$ : roi: " + fakeLongOrders.get(i).getRoi() + " : pnl: " + fakeLongOrders.get(i).getPnl() + " : " + fakeLongOrders.get(i).getTimeOpened());
                staticFakeOrderRepository.save(fakeLongOrders.get(i));
            }
            else if(candlestick.getLow()<fakeLongOrders.get(i).getStopLoss()){
                fakeLongOrders.get(i).setExitPrice(candlestick.getClose());
                fakeLongOrders.get(i).setRoi(fakeLongOrders.get(i).getExitPrice() / fakeLongOrders.get(i).getEntryPrice());
                fakeLongOrders.get(i).setRoi(fakeLongOrders.get(i).getRoi()*0.9998);
                fakeLongOrders.get(i).setPnl(startingBalance*fakeLongOrders.get(i).getRoi());
                fakeLongOrders.get(i).setPnl(fakeLongOrders.get(i).getPnl()-startingBalance);
                fakeLongOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                startingBalance+=fakeLongOrders.get(i).getPnl();
                System.out.println("long loss : roi: " + fakeLongOrders.get(i).getRoi() + " : pnl: " + fakeLongOrders.get(i).getPnl() + " : " + fakeLongOrders.get(i).getTimeOpened());
                staticFakeOrderRepository.save(fakeLongOrders.get(i));
            }
        }
        return startingBalance;
    }
    public static Candlestick getDailyHigh(ArrayList<Candlestick> simulated){
        Candlestick highest = simulated.get(0);
        for(int i = 0; i<simulated.size();i++){
            if(simulated.get(i).getHigh()>highest.getHigh()){
                highest = simulated.get(i);
            }
        }
        return highest;
    }
    public static Candlestick getDailyLow(ArrayList<Candlestick> simulated){
        Candlestick lowest = simulated.get(0);
        for(int i = 0; i<simulated.size();i++){
            if(simulated.get(i).getLow()<lowest.getLow()){
                lowest = simulated.get(i);
            }
        }
        return lowest;
    }
    public static boolean isCurrentYoungerThan4Ticks(Candlestick currentCandlestick, Candlestick breakoutEndCandlestick, int minutes){
        Instant currentDate = parseDate(currentCandlestick.getOpenTime()).toInstant();
        Instant breakoutEndDate = parseDate(breakoutEndCandlestick.getOpenTime()).toInstant();
        if(currentDate.isBefore(breakoutEndDate.plus(minutes, ChronoUnit.MINUTES))){
            return true;
        }
        return false;
    }
    public static Date parseDate(String date) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(date);
        } catch (ParseException e) {
            return null;
        }
    }
    public static ArrayList<Candlestick> removeOverflow(ArrayList<Candlestick> simulatedCandlesticks, int stepBack){
        if(simulatedCandlesticks.size()>stepBack){
            simulatedCandlesticks.remove(simulatedCandlesticks.size()-stepBack-1);
        }
        return simulatedCandlesticks;
    }
    @PostConstruct
    public void init() throws LoginException {
        staticFakeOrderRepository = fakeOrderRepository;
        staticUserRepository = userRepository;
        staticCandlestickRepository = candlestickRepository;
    }
    @Transactional
    public void removeCandlesticksData(){
        System.out.println("removing candlestick data");
        staticCandlestickRepository.deleteAll(staticCandlestickRepository.findAll());
        System.out.println("removed candlestick data");
    }
    public static <T> ArrayList<T> removeDuplicates(ArrayList<T> list)
    {

        // Create a new ArrayList
        ArrayList<T> newList = new ArrayList<T>();

        // Traverse through the first list
        for (T element : list) {

            // If this element is not present in newList
            // then add it
            if (!newList.contains(element)) {

                newList.add(element);
            }
        }

        // return the new list
        return newList;
    }
    public void saveCandlesticksData()  {
        try{
            System.out.println("saving new candlestick data");
            Document doc = Jsoup.connect("https://cbt3.herokuapp.com/test-data")
                    .maxBodySize(0)
                    .ignoreContentType(true)
                    .get();
            JsonElement json = JsonParser.parseString(doc.text());
            Gson g = new Gson();
            ArrayList<Candlestick> candlesticks = new ArrayList<>();
            for (JsonElement candlestickItem : json.getAsJsonArray()) {
                Candlestick candlestick = g.fromJson(candlestickItem, Candlestick.class);
                candlesticks.add(candlestick);
            }
            candlesticks = removeDuplicates(candlesticks);
            staticCandlestickRepository.saveAll(candlesticks);
            System.out.println("saved new candlestick data; size: " + candlesticks.size());
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @Transactional
    public void removeFakeOrders(){
        System.out.println("removing fake orders");
        staticFakeOrderRepository.deleteAll(staticFakeOrderRepository.findAll());
        System.out.println("removed fake orders");
    }
}