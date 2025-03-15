package com.example.demo.model;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter
public class LoginRequest {
    private String username;
    private String password;

    public Object getUsername() {
        return username;
    }

    public Object getPassword() {
        return password;
    }

}
