package com.example.backtestcbt3.Service;


import com.example.backtestcbt3.model.User;
import com.example.backtestcbt3.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;


import java.util.List;

@Service
public class UserServiceImp implements UserService {
    @Autowired
    private BCryptPasswordEncoder encrypt;


    @Autowired
    private UserRepository repo;

    @Override
    public void save(User user) {
        repo.save(user);
    }

    @Override
    public String enCryptedPassword(User user) {
        return encrypt.encode(user.getPassword());
    }

    @Override
    public List<User> getAll() {
        return (List<User>) repo.findAll();
    }

}
