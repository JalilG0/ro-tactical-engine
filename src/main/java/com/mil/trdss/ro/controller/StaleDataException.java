package com.mil.trdss.ro.controller;

public class StaleDataException extends RuntimeException {

    public StaleDataException(String message) {
        super(message);
    }
}
