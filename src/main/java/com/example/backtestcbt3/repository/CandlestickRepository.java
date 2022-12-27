package com.example.backtestcbt3.repository;

import com.example.backtestcbt3.model.Candlestick;
import com.example.backtestcbt3.model.FakeOrder;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;

@Repository
public interface CandlestickRepository extends CrudRepository<Candlestick,Long> {
    Candlestick findById(long id);
    ArrayList<Candlestick> findAll();
}
