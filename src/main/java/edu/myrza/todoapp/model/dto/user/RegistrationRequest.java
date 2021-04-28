package edu.myrza.todoapp.model.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationRequest {

    private String username;
    private String password;
    private String email;

}
