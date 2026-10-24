package com.eclipse.tarjanapa.web;

import com.eclipse.tarjanapa.service.NoDataException;
import com.eclipse.tarjanapa.tranzy.TranzyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Transformă excepțiile în răspunsuri JSON cu mesaj în română. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoDataException.class)
    ResponseEntity<ApiDtos.ErrorDto> noData(NoDataException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiDtos.ErrorDto(e.getMessage(), "NO_DATA"));
    }

    @ExceptionHandler(TranzyException.class)
    ResponseEntity<ApiDtos.ErrorDto> tranzy(TranzyException e) {
        HttpStatus status = switch (e.kind()) {
            case MISSING_API_KEY, MISSING_AGENCY -> HttpStatus.SERVICE_UNAVAILABLE;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ApiDtos.ErrorDto(e.getMessage(), e.kind().name()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiDtos.ErrorDto> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiDtos.ErrorDto(e.getMessage(), "BAD_REQUEST"));
    }
}
