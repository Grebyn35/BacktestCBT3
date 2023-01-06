package com.example.backtestcbt3.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import java.io.Serializable;

@Data
@Entity
public class Candlestick implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @SerializedName("dateCreated")
    private String openTime;
    @SerializedName("open")
    private double open;
    @SerializedName("high")
    private double high;
    @SerializedName("low")
    private double low;
    @SerializedName("close")
    private double close;
    @SerializedName("volume")
    private double volume;

    @SerializedName("ofiBullish")
    private double ofiBullish;
    @SerializedName("ofiBearish")
    private double ofiBearish;

    @SerializedName("delta")
    private double delta;
    @SerializedName("maxDelta")
    private double maxDelta;
    @SerializedName("minDelta")
    private double minDelta;
    @SerializedName("bullishImbalances")
    private int bullishImbalances;
    @SerializedName("bearishImbalances")
    private int bearishImbalances;


    @SerializedName("ema")
    private double ema;
}
