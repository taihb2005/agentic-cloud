package vn.edu.hust.kat.storageservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INSUFFICIENT_STORAGE)
public class InsufficientException extends RuntimeException {
    public InsufficientException(String message) {super(message);}
}
