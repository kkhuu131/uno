package com.kkhuu131.uno.backend.exception;

import com.kkhuu131.uno.backend.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(GameAlreadyFinishedException.class)
	public ResponseEntity<ErrorResponse> handleGameAlreadyFinished(GameAlreadyFinishedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
	}
}
