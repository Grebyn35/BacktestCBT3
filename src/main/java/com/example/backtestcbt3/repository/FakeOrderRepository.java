package com.example.backtestcbt3.repository;


import com.example.backtestcbt3.model.FakeOrder;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Array;
import java.util.ArrayList;

@Repository
public interface FakeOrderRepository extends CrudRepository<FakeOrder,Long> {

    FakeOrder findById(long id);
    ArrayList<FakeOrder> findAllBySide(String side);
    FakeOrder findByCandlestickId(long id);
    ArrayList<FakeOrder> findAll();
    ArrayList<FakeOrder> findAllBySideAndExitPrice(String side,double greaterThan);
    ArrayList<FakeOrder> findAllBySideAndExitPriceIsGreaterThan(String side,double greaterThan);
    ArrayList<FakeOrder> findAllByExitPriceIsGreaterThan(double greaterThan);
}