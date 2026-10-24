package com.eclipse.tarjanapa.service;

/** Datele rețelei nu sunt (încă) încărcate; mesajul explică de ce. */
public class NoDataException extends RuntimeException {

    public NoDataException(String message) {
        super(message);
    }
}
