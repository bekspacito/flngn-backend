package edu.aidana.todoapp.exceptions;

/*
*  These errors are covered and expected in use cases and the system either
* can fully handle and recover from these exceptions/errors or send the client a clean and unambiguous
* error message.
* */

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BussinesException extends RuntimeException {

    public enum Code {
        AUTH_001 // Authentication failed (incorrect username or password)
    }

    private Code code;

}
