package vn.edu.hust.kat.storageservice.exception;

public class ShareExpiredException extends RuntimeException {
    public ShareExpiredException(String message) {
        super(message);
    }
}
