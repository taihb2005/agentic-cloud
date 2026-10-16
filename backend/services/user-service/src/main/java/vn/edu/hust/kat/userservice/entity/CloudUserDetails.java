package vn.edu.hust.kat.userservice.entity;

import org.springframework.security.core.userdetails.UserDetails;

public interface CloudUserDetails extends UserDetails {
    String getUserId();
}
