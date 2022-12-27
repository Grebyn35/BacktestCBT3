package com.example.backtestcbt3.model;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.io.Serializable;

@Data
@Entity
public class FakeOrder implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private double entryPrice;
    private double exitPrice;
    private double takeProfit;
    private double stopLoss;
    private double roi;
    private double pnl;
    private double dollarQty = 1000;
    private String side;
    private String timeOpened;
    private long candlestickId;
}
