package com.example.Interfaces;

import io.jsonwebtoken.Claims;
import java.util.List;

public interface IJwtService {

    String generateToken(int id, List<String> roles);


    Claims validateToken(String token);


    boolean isTokenExpired(String token);
}