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

import java.io.*;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Controller
public class UserController {
    private static final DecimalFormat df2d = new DecimalFormat("0.00");
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
    @GetMapping("/visualize-ml")
    public String visualiseML(Model model) throws IOException {
        ArrayList<Double> actualPricesList = new ArrayList<Double>();
        ArrayList<Double> predictedPricesList = new ArrayList<Double>();
        String row;
        BufferedReader csvReaderActual = new BufferedReader(new FileReader("C:\\Users\\Grebyn\\Downloads\\actual_prices.csv"));
        BufferedReader csvReaderPredicted = new BufferedReader(new FileReader("C:\\Users\\Grebyn\\Downloads\\predicted_prices.csv"));

        while ((row = csvReaderActual.readLine()) != null) {
            actualPricesList.add(Double.parseDouble(row));
        }
        while ((row = csvReaderPredicted.readLine()) != null) {
            predictedPricesList.add(Double.parseDouble(row));
        }
        model.addAttribute("actualPricesList", actualPricesList);
        model.addAttribute("predictedPricesList", predictedPricesList);
        return "visualiser_ml";
    }
    @GetMapping("/backtest-data/{stepBack}/{takeProfit}")
    public String backtestData(@PathVariable int stepBack, @PathVariable double takeProfit, Model model) throws FileNotFoundException, ParseException, InterruptedException {
        removeFakeOrders();
        strategyTester(stepBack, takeProfit, model);
        visualiseDataModel(model);
        return "visualiser";
    }
    @GetMapping("/visualise-data")
    public String visualiseData(Model model){
        visualiseDataModel(model);
        return "visualiser";
    }
    @GetMapping("/create-user")
    public String createUser(Model model){
        User user = new User();
        user.setEmail("elliot@ensotech.io");
        user.setEquity(500);
        user.setLeverage(1);
        user.setPassword("123");
        user.setOrderQty(0.25);
        user.setRole("ROLE_USER");
        staticUserRepository.save(user);
        return "visualiser";
    }
    @GetMapping("/reset-equity/{equity}/{leverage}")
    public String resetEquity(Model model, @PathVariable long equity, @PathVariable double leverage){
        User user = staticUserRepository.findAll().get(0);
        user.setEquity(equity);
        user.setLeverage(leverage);
        user.setAvailableBalance(user.getEquity());
        System.out.println("reset key values to " + equity + " equity, " + leverage + " leverage");
        staticUserRepository.save(user);
        return "visualiser";
    }
    @GetMapping("/print-data")
    public String printData(Model model) throws IOException {
        ArrayList<Candlestick> candlesticks = staticCandlestickRepository.findAll();
        for(int i = 0; i<candlesticks.size();i++){
            PrintWriter pw = new PrintWriter(new FileWriter("C:\\Users\\Grebyn\\Downloads\\test-data.csv", true));
            pw.println(candlesticks.get(i).getClose() + "," +candlesticks.get(i).getOpen() + "," +candlesticks.get(i).getHigh() + "," +candlesticks.get(i).getLow() + "," +
                    candlesticks.get(i).getDelta() + "," +candlesticks.get(i).getMaxDelta() + "," +candlesticks.get(i).getMinDelta() + "," +candlesticks.get(i).getVolume() + "," +candlesticks.get(i).getId());
            pw.close();
        }
        return "visualiser";
    }
    public void visualiseDataModel(Model model){
        User user = staticUserRepository.findAll().get(0);
        ArrayList<Double> dataTotal = new ArrayList<>();
        ArrayList<String> dateTotal = new ArrayList<>();

        ArrayList<Double> dataLong = new ArrayList<>();
        ArrayList<String> dateLong = new ArrayList<>();

        ArrayList<Double> dataShort = new ArrayList<>();
        ArrayList<String> dateShort = new ArrayList<>();

        ArrayList<Double> dataVolume = new ArrayList<>();
        ArrayList<String> dateVolume = new ArrayList<>();
        double startingBalanceTotal = user.getEquity();
        double startingBalanceLong = user.getEquity();
        double startingBalanceShort = user.getEquity();
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

        for(int i = 0; i<candlesticks.size();i++){
            dataVolume.add(candlesticks.get(i).getVolume());
            dateVolume.add(candlesticks.get(i).getOpenTime());
        }

        for(int i = 0; i<fakeTotalOrders.size();i++){
            startingBalanceTotal += fakeTotalOrders.get(i).getPnl();
            dataTotal.add(startingBalanceTotal);
            dateTotal.add(fakeTotalOrders.get(i).getTimeOpened());
        }

        model.addAttribute("dataVolume", dataVolume.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dateVolume", dateVolume.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dataLong", dataLong.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dataShort", dataShort.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dataTotal", dataTotal.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dateTotal", dateTotal.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dateLong", dateLong.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("dateShort", dateShort.toString().replaceAll("]","").replaceAll("\\[", "").replaceAll(" ", ""));
        model.addAttribute("candlesticks", makeHigherTimeframeCandlestick(candlesticks, 5));
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
    public double calcSma(double length, ArrayList<Candlestick> candlesticks){
        double sum = 0;
        for(int i = 0; i<length;i++){
            sum += candlesticks.get(i).getClose();
        }
        return sum/length;
    }
    public double calcEma(ArrayList<Candlestick> candlesticks, double length){
        double multiplier = 2/ (length+1);
        double yesterdayEma = 0;
        if(candlesticks.get(candlesticks.size()-2).getEma()!=0) {
            yesterdayEma = candlesticks.get(candlesticks.size() - 2).getEma();
        }
        else{
            yesterdayEma = calcSma(length, candlesticks);
        }
        double ema = candlesticks.get(candlesticks.size()-1).getClose() * multiplier + yesterdayEma * (1-multiplier);
        return ema;
    }
    public Model strategyTester(int stepBack, double takeProfit, Model model) throws InterruptedException, FileNotFoundException, ParseException {

        //The daily high and low. Symbolizes liquidity as a primary-key in the strategy
        Candlestick dailyHigh = null;
        Candlestick dailyLow = null;

        //Whole dataset
        ArrayList<Candlestick> candlesticks = staticCandlestickRepository.findAll();
        ArrayList<Candlestick> simulatedCandlesticks = new ArrayList<>();
        for(int i = 0; i<candlesticks.size();i++){
            //Adding a new candlestick like below simulates realtime data with a new opne, close low, high
            simulatedCandlesticks.add(candlesticks.get(i));

            //Removes old values in the simulated data not being used for iteration efficiency
            simulatedCandlesticks = removeOverflow(simulatedCandlesticks, stepBack);

            //Check if a long position is open
            monitorLongPositions(simulatedCandlesticks.get(simulatedCandlesticks.size()-1));
            //Check if a short position is open
            monitorShortPositions(simulatedCandlesticks.get(simulatedCandlesticks.size()-1));

            if(simulatedCandlesticks.size()>stepBack-1){
                simulatedCandlesticks.get(simulatedCandlesticks.size()-1).setAtr((atr(simulatedCandlesticks)));
                simulatedCandlesticks.get(simulatedCandlesticks.size()-1).setEma(calcEma(simulatedCandlesticks,stepBack));
                double volume = dailyHigh.getHigh() / dailyLow.getLow();
                if(volumeImbalanceLong(simulatedCandlesticks) && volume>=1.007 && simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose() > simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getEma()){
                    createFakeLong(simulatedCandlesticks, takeProfit, dailyLow, atr(simulatedCandlesticks));
                }
                else if(volumeImbalanceShort(simulatedCandlesticks) && volume>=1.007 && simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose() < simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getEma()){
                    createFakeShort(simulatedCandlesticks, takeProfit, dailyHigh, atr(simulatedCandlesticks));
                }
            }
            dailyHigh = getDailyHigh(simulatedCandlesticks);
            dailyLow = getDailyLow(simulatedCandlesticks);
        }
        closeOpenPostions(simulatedCandlesticks.get(simulatedCandlesticks.size()-1));
        //Print the results. Static variables are being used to compare best previous iterations of params
        return returnResults(model);
    }
    public static void closeOpenPostions(Candlestick candlestick) throws InterruptedException {
        ArrayList<FakeOrder> fakeShortOrders = staticFakeOrderRepository.findAllByExitPrice(0);
        User user = staticUserRepository.findAll().get(0);
        for(int i = 0; i<fakeShortOrders.size();i++) {
            fakeShortOrders.get(i).setExitPrice(candlestick.getClose());
            fakeShortOrders.get(i).setRoi(fakeShortOrders.get(i).getEntryPrice() / fakeShortOrders.get(i).getExitPrice());
            fakeShortOrders.get(i).setPnl((fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice())*fakeShortOrders.get(i).getRoi() - (fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice()));
            fakeShortOrders.get(i).setTimeClosed(candlestick.getOpenTime());
            user.setAvailableBalance(user.getAvailableBalance() + (fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice()+fakeShortOrders.get(i).getPnl()));
            staticFakeOrderRepository.save(fakeShortOrders.get(i));
        }
        staticUserRepository.save(user);
    }
    public double atr(ArrayList<Candlestick> candlesticks){
        ArrayList<Double> trueRange = new ArrayList<>();
        ArrayList<Double> averageTrueRange = new ArrayList<>();
        for(int i = candlesticks.size()-14; i<candlesticks.size();i++){
            double hl = candlesticks.get(i).getHigh() - candlesticks.get(i).getLow();
            double ho = Math.abs(candlesticks.get(i).getHigh() - candlesticks.get(i).getOpen());
            double lo = Math.abs(candlesticks.get(i).getLow() - candlesticks.get(i).getOpen());
            trueRange.add(Math.max(Math.max(hl, ho), lo));
        }
        // calculate the ATR
        double prevATR = candlesticks.get(candlesticks.size()-2).getAtr();
        for (int i = 0; i < trueRange.size(); i++) {
            double currentATR = (prevATR * (14 - 1) + trueRange.get(i)) / 14;
            averageTrueRange.add(currentATR);
            prevATR = currentATR;
        }
        return averageTrueRange.get(averageTrueRange.size() - 1);
    }
    public boolean volumeImbalanceLong(ArrayList<Candlestick> candlesticks){
        if(candlesticks.get(candlesticks.size()-1).getVolume() > candlesticks.get(candlesticks.size()-2).getVolume() && candlesticks.get(candlesticks.size()-2).getVolume() > candlesticks.get(candlesticks.size()-3).getVolume() && candlesticks.get(candlesticks.size()-1).getVolume()>3000){
            if(candlesticks.get(candlesticks.size()-1).getDelta()>600) {
                return true;
            }
        }
        return false;
    }
    public boolean volumeImbalanceShort(ArrayList<Candlestick> candlesticks){
        if(candlesticks.get(candlesticks.size()-1).getVolume() > candlesticks.get(candlesticks.size()-2).getVolume() && candlesticks.get(candlesticks.size()-2).getVolume() > candlesticks.get(candlesticks.size()-3).getVolume() && candlesticks.get(candlesticks.size()-1).getVolume()>3000){
            if(candlesticks.get(candlesticks.size()-1).getDelta()<-600) {
                return true;
            }
        }
        return false;
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
    public ArrayList<Candlestick> makeHigherTimeframe(ArrayList<Candlestick> candlesticks, int size){
        ArrayList<Candlestick> candlesticksHigher = new ArrayList<>();
        int count = 0;
        Candlestick candlestick = new Candlestick();
        double deltaMax = 0;
        double deltaMin = 0;
        for(int i = 0; i<candlesticks.size();i++){
            if(count==size){
                candlestick.setDelta(candlesticks.get(i).getDelta());
                if(candlestick.getDelta()>0){
                    candlestick.setOfiBullish(calculateOfi(candlestick.getDelta(), candlestick.getMaxDelta(), candlestick.getMinDelta()));
                }
                else{
                    candlestick.setOfiBearish(calculateOfi(candlestick.getDelta(), candlestick.getMaxDelta(), candlestick.getMinDelta()));
                }
                candlesticksHigher.add(candlestick);
                candlestick = null;
                count = 0;
                deltaMax = 0;
                deltaMin = 0;
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
            else if(candlesticks.get(i).getMinDelta()<deltaMin){
                deltaMin = candlesticks.get(i).getMinDelta();
                candlestick.setMinDelta(candlesticks.get(i).getMinDelta());
            }
            else if(candlesticks.get(i).getMaxDelta()>deltaMax){
                deltaMax = candlesticks.get(i).getMaxDelta();
                candlestick.setMaxDelta(candlesticks.get(i).getMaxDelta());
            }
            count++;
        }
        return candlesticksHigher;
    }
    public static double calculateOfi(double delta, double deltaMax, double deltaMin){
        double positiveSignal;
        if(delta>0){
            positiveSignal = delta / deltaMax;
        }
        else{
            positiveSignal = delta / deltaMin;
        }
        if(positiveSignal>0){
            return positiveSignal;
        }
        return 0;
    }
    public static Model returnResults(Model model){
        User user = staticUserRepository.findAll().get(0);
        double profits = 0;
        double wins = 0;
        double losses = 0;
        double roiDeltaWin = 0;
        double roiDeltaLoss = 0;

        double biggestWin = 1;
        double biggestLoss = 1;
        ArrayList<FakeOrder> fakeOrders = staticFakeOrderRepository.findAllByExitPriceIsGreaterThan(0);
        for(int i = 0; i<fakeOrders.size();i++){
            profits += fakeOrders.get(i).getPnl();
            if(fakeOrders.get(i).getPnl()>0){
                roiDeltaWin+= fakeOrders.get(i).getRoi();
                wins++;
            }
            else{
                roiDeltaLoss+= fakeOrders.get(i).getRoi();
                losses++;
            }
            if(fakeOrders.get(i).getRoi()>biggestWin){
                biggestWin = fakeOrders.get(i).getRoi();
            }
            else if(fakeOrders.get(i).getRoi()<biggestLoss){
                biggestLoss = fakeOrders.get(i).getRoi();
            }
        }
        System.out.println("average win size   : " + (roiDeltaWin/wins));
        System.out.println("average loss size  : " + (roiDeltaLoss/losses));
        System.out.println("time period        : " + fakeOrders.get(0).getTimeOpened() + " to " + fakeOrders.get(fakeOrders.size()-1).getTimeOpened());
        System.out.println("starting balance   : " + user.getEquity() + " $");
        System.out.println("profit             : " + (int)profits + " $");
        System.out.println("wins               : " + (wins));
        System.out.println("losses             : " + (losses));
        System.out.println("biggest win        : " + ((biggestWin)-1)*100 + " %");
        System.out.println("biggest loss       : " + ((biggestLoss)-1)*100 + " %");
        System.out.println("wirate             : " + (wins/(wins+losses))*100 + " %");
        System.out.println("ROI                : " + ((profits)/user.getEquity())*100 + " %");
        System.out.println("---");
        model.addAttribute("totalTrades", fakeOrders.size());
        model.addAttribute("winningTrades", (int)wins);
        model.addAttribute("losingTrades", (int)losses);
        model.addAttribute("winrate", Double.parseDouble(df2d.format((wins/(wins+losses))*100).replaceAll(",", ".").replaceAll("???", "-")));
        model.addAttribute("roi",  Double.parseDouble(df2d.format(((profits)/user.getEquity())*100).replaceAll(",", ".").replaceAll("???", "-")));
        model.addAttribute("pnl", (int)profits);
        model.addAttribute("leverage", (int)user.getLeverage());
        model.addAttribute("startingBalance", (int)(user.getEquity()));
        model.addAttribute("newBalance", (int)(user.getEquity() + (int)profits));
        user.setEquity(user.getAvailableBalance());
        staticUserRepository.save(user);
        return model;
    }
    public static void createFakeShort(ArrayList<Candlestick> simulatedCandlesticks, double takeProfit, Candlestick dailyHigh, double atr){
        User user = staticUserRepository.findAll().get(0);
        FakeOrder fakeOrder = new FakeOrder();
        fakeOrder.setSide("Sell");
        fakeOrder.setOfiBearish(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOfiBearish());
        fakeOrder.setOfiBullish(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOfiBullish());
        fakeOrder.setVolume(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getVolume());
        fakeOrder.setDelta(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getDelta());
        fakeOrder.setMaxDelta(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getMaxDelta());
        fakeOrder.setMinDelta(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getMinDelta());
        fakeOrder.setCandlestickId(dailyHigh.getId());
        fakeOrder.setTimeOpened(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOpenTime());
        fakeOrder.setEntryPrice(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose());
        fakeOrder.setStopLoss(dailyHigh.getHigh());
        fakeOrder.setTakeProfit(calcTPShort(fakeOrder.getStopLoss(), fakeOrder.getEntryPrice(), takeProfit));
        fakeOrder.setQty(((user.getEquity() * user.getOrderQty())) / fakeOrder.getEntryPrice());
        if(user.getAvailableBalance()>(user.getEquity()*user.getOrderQty()) && fakeOrder.getStopLoss()<fakeOrder.getEntryPrice()*1.015 && fakeOrder.getStopLoss()>fakeOrder.getEntryPrice()){
            user.setAvailableBalance(user.getAvailableBalance()-(fakeOrder.getQty()*fakeOrder.getEntryPrice()));
            staticUserRepository.save(user);
            staticFakeOrderRepository.save(fakeOrder);
        }
    }
    public static void createFakeLong(ArrayList<Candlestick> simulatedCandlesticks, double takeProfit, Candlestick dailyLow, double atr){
        User user = staticUserRepository.findAll().get(0);
        FakeOrder fakeOrder = new FakeOrder();
        fakeOrder.setSide("Buy");
        fakeOrder.setOfiBearish(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOfiBearish());
        fakeOrder.setOfiBullish(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOfiBullish());
        fakeOrder.setVolume(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getVolume());
        fakeOrder.setDelta(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getDelta());
        fakeOrder.setMaxDelta(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getMaxDelta());
        fakeOrder.setMinDelta(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getMinDelta());
        fakeOrder.setCandlestickId(dailyLow.getId());
        fakeOrder.setTimeOpened(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getOpenTime());
        fakeOrder.setEntryPrice(simulatedCandlesticks.get(simulatedCandlesticks.size()-1).getClose());
        fakeOrder.setStopLoss(dailyLow.getClose());
        fakeOrder.setTakeProfit(calcTPLong(fakeOrder.getStopLoss(), fakeOrder.getEntryPrice(), takeProfit));
        fakeOrder.setQty(((user.getEquity() * user.getOrderQty())) / fakeOrder.getEntryPrice());
        if(user.getAvailableBalance()>(user.getEquity()*user.getOrderQty()) && fakeOrder.getStopLoss()>fakeOrder.getEntryPrice()*0.985 && fakeOrder.getStopLoss()<fakeOrder.getEntryPrice()){
            user.setAvailableBalance(user.getAvailableBalance()-(fakeOrder.getQty()*fakeOrder.getEntryPrice()));
            staticUserRepository.save(user);
            staticFakeOrderRepository.save(fakeOrder);
        }
    }
    //Denna kan vara skitbra att implementera efter ett tag av komfirmation p?? att strategin funkar. Beh??ver korrigeras
    public static double setHedgeQty(FakeOrder fakeOrder, User user, String oposingSide){
        ArrayList<FakeOrder> opposingOrders = staticFakeOrderRepository.findAllBySideAndExitPrice(oposingSide, 0);
        if(opposingOrders.size()>0) {
            fakeOrder.setQty(((user.getEquity() * user.getOrderQty()) * (opposingOrders.size()+1)) / fakeOrder.getEntryPrice());
        }
        else{
            fakeOrder.setQty(((user.getEquity() * user.getOrderQty())) / fakeOrder.getEntryPrice());
        }
        return fakeOrder.getQty();
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
    public static void monitorShortPositions(Candlestick candlestick) throws InterruptedException {
        ArrayList<FakeOrder> fakeShortOrders = staticFakeOrderRepository.findAllBySideAndExitPrice("Sell", 0);
        User user = staticUserRepository.findAll().get(0);
        for(int i = 0; i<fakeShortOrders.size();i++) {
            if (candlestick.getLow() < fakeShortOrders.get(i).getTakeProfit() && candlestick.getHigh() < fakeShortOrders.get(i).getStopLoss()) {
                fakeShortOrders.get(i).setExitPrice(fakeShortOrders.get(i).getTakeProfit());
                fakeShortOrders.get(i).setRoi((fakeShortOrders.get(i).getEntryPrice() / fakeShortOrders.get(i).getExitPrice())*0.9998);
                fakeShortOrders.get(i).setPnl((fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice())*fakeShortOrders.get(i).getRoi() - (fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice()));
                fakeShortOrders.get(i).setPnl(fakeShortOrders.get(i).getPnl()*user.getLeverage());
                fakeShortOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                System.out.println("short $$$ : roi: " + fakeShortOrders.get(i).getRoi() + " : pnl: " + fakeShortOrders.get(i).getPnl() + " : " + fakeShortOrders.get(i).getTimeOpened());
                user.setAvailableBalance(user.getAvailableBalance() + ((fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice())+fakeShortOrders.get(i).getPnl()));
                staticUserRepository.save(user);
                staticFakeOrderRepository.save(fakeShortOrders.get(i));
            } else if (candlestick.getHigh() > fakeShortOrders.get(i).getStopLoss()) {
                fakeShortOrders.get(i).setExitPrice(fakeShortOrders.get(i).getStopLoss());
                fakeShortOrders.get(i).setRoi((fakeShortOrders.get(i).getEntryPrice() / fakeShortOrders.get(i).getExitPrice())*0.9998);
                fakeShortOrders.get(i).setPnl((fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice())*fakeShortOrders.get(i).getRoi() - (fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice()));
                fakeShortOrders.get(i).setPnl(fakeShortOrders.get(i).getPnl()*user.getLeverage());
                fakeShortOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                System.out.println("short loss : roi: " + fakeShortOrders.get(i).getRoi() + " : pnl: " + fakeShortOrders.get(i).getPnl() + " : " + fakeShortOrders.get(i).getTimeOpened());
                user.setAvailableBalance(user.getAvailableBalance() + ((fakeShortOrders.get(i).getQty()*fakeShortOrders.get(i).getEntryPrice())+fakeShortOrders.get(i).getPnl()));
                staticUserRepository.save(user);
                staticFakeOrderRepository.save(fakeShortOrders.get(i));
            }
        }
    }
    public static void monitorLongPositions(Candlestick candlestick) throws InterruptedException {
        User user = staticUserRepository.findAll().get(0);
        ArrayList<FakeOrder> fakeLongOrders = staticFakeOrderRepository.findAllBySideAndExitPrice("Buy", 0);
        for(int i = 0; i<fakeLongOrders.size();i++){
            if(candlestick.getHigh()>fakeLongOrders.get(i).getTakeProfit() && candlestick.getLow() > fakeLongOrders.get(i).getStopLoss()){
                fakeLongOrders.get(i).setExitPrice(fakeLongOrders.get(i).getTakeProfit());
                fakeLongOrders.get(i).setRoi((fakeLongOrders.get(i).getExitPrice() / fakeLongOrders.get(i).getEntryPrice())*0.9998);
                fakeLongOrders.get(i).setPnl((fakeLongOrders.get(i).getQty()*fakeLongOrders.get(i).getEntryPrice())*fakeLongOrders.get(i).getRoi() - (fakeLongOrders.get(i).getQty()*fakeLongOrders.get(i).getEntryPrice()));
                fakeLongOrders.get(i).setPnl(fakeLongOrders.get(i).getPnl()*user.getLeverage());
                fakeLongOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                System.out.println("long $$$ : roi: " + fakeLongOrders.get(i).getRoi() + " : pnl: " + fakeLongOrders.get(i).getPnl() + " : " + fakeLongOrders.get(i).getTimeOpened());
                user.setAvailableBalance(user.getAvailableBalance() + ((fakeLongOrders.get(i).getQty()*fakeLongOrders.get(i).getEntryPrice())+fakeLongOrders.get(i).getPnl()));
                staticUserRepository.save(user);
                staticFakeOrderRepository.save(fakeLongOrders.get(i));
            }
            else if(candlestick.getLow()<fakeLongOrders.get(i).getStopLoss()){
                fakeLongOrders.get(i).setExitPrice(fakeLongOrders.get(i).getStopLoss());
                fakeLongOrders.get(i).setRoi((fakeLongOrders.get(i).getExitPrice() / fakeLongOrders.get(i).getEntryPrice())*0.9998);
                fakeLongOrders.get(i).setPnl((fakeLongOrders.get(i).getQty()*fakeLongOrders.get(i).getEntryPrice())*fakeLongOrders.get(i).getRoi() - (fakeLongOrders.get(i).getQty()*fakeLongOrders.get(i).getEntryPrice()));
                fakeLongOrders.get(i).setPnl(fakeLongOrders.get(i).getPnl()*user.getLeverage());
                fakeLongOrders.get(i).setTimeClosed(candlestick.getOpenTime());
                System.out.println("long loss : roi: " + fakeLongOrders.get(i).getRoi() + " : pnl: " + fakeLongOrders.get(i).getPnl() + " : " + fakeLongOrders.get(i).getTimeOpened());
                user.setAvailableBalance(user.getAvailableBalance() + ((fakeLongOrders.get(i).getQty()*fakeLongOrders.get(i).getEntryPrice())+fakeLongOrders.get(i).getPnl()));
                staticUserRepository.save(user);
                staticFakeOrderRepository.save(fakeLongOrders.get(i));
            }
        }
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
    public static boolean isCurrentOlderThan(Candlestick currentCandles, Candlestick openCandle, int minutes){
        Instant currentDate = parseDate(currentCandles.getOpenTime()).toInstant();
        Instant breakoutEndDate = parseDate(openCandle.getOpenTime()).toInstant();
        if(currentDate.isAfter(breakoutEndDate.plus(minutes, ChronoUnit.MINUTES))){
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