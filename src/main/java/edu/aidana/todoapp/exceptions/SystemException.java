package edu.aidana.todoapp.exceptions;

/*
*  These exceptions indicate errors that must not happen.
*   (e.g. IOException when creating a folder). Developers must respond to these king of
*   errors as soon as possible and fix them. When an SystemException is thrown the system
*   will respond to user request with code '500' and some general message like
*   'Couldn't process the request. Try again, later'
* */

import lombok.Getter;

@Getter
public class SystemException extends RuntimeException {
    public SystemException(Exception ex, String msg) {
        super(msg, ex);
    }
}
