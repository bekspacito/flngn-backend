package edu.myrza.todoapp.controller;

import edu.myrza.todoapp.exceptions.BussinesException;
import edu.myrza.todoapp.model.dto.user.LoginRequest;
import edu.myrza.todoapp.model.dto.user.LoginResponse;
import edu.myrza.todoapp.model.dto.user.RegistrationRequest;
import edu.myrza.todoapp.model.dto.user.UserDto;
import edu.myrza.todoapp.model.entity.User;
import edu.myrza.todoapp.service.FileService;
import edu.myrza.todoapp.service.UserService;
import edu.myrza.todoapp.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UserController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Autowired
    public UserController(
            AuthenticationManager authenticationManager,
            UserService userService,
            JwtUtil jwtUtil)
    {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody LoginRequest request) {

        String username = request.getUsername();
        String password = request.getPassword();

        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException ex) {
            throw new BussinesException(BussinesException.Code.AUTH_001);
        }

        User user = userService.loadUserByUsername(username);

        String token = jwtUtil.generateToken(user);

        return ResponseEntity.ok(new LoginResponse(username, user.getEmail(), token, username));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {

        //Here we should save token into 'black list' table
        return ResponseEntity.ok().build();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerNewUser(@RequestBody RegistrationRequest req) {
        return ResponseEntity.ok(userService.registerUser(req));
    }

    @GetMapping("/user/search/{namechunk}")
    public List<UserDto> searchByName(@PathVariable("namechunk") String namechunk) {
        return userService.searchByName(namechunk);
    }
}
