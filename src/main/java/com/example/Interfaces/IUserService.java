package com.example.Interfaces;

import com.example.model.User;
import java.util.List;

public interface IUserService {
    boolean signUp(User user);
    User login(String username, String password);
    User getById(int id);
    List<User> getAllUsers();
}