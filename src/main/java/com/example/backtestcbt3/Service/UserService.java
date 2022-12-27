package com.example.backtestcbt3.Service;


import com.example.backtestcbt3.model.User;

import java.util.List;

public interface UserService {
    public void save(User user);
    public String enCryptedPassword(User user);
    public List<User> getAll();

}
