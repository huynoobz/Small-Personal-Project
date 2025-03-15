package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        if (!userRepository.findById(id).isEmpty()) 
            return userRepository.findById(id);
        else
            throw new IndexOutOfBoundsException("User không tồn tại");
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        if (!userRepository.findById(id).isEmpty()) 
            userRepository.deleteById(id);
        else
            throw new IndexOutOfBoundsException("User không tồn tại");
    }
}
