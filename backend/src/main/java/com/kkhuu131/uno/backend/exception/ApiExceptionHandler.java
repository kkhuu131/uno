package com.kkhuu131.uno.backend.exception;

import com.kkhuu131.uno.backend.web.dto.ErrorResponse;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(GameAlreadyFinishedException.class)
	public ResponseEntity<ErrorResponse> handleGameAlreadyFinished(GameAlreadyFinishedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		String message =
				ex.getBindingResult().getFieldErrors().stream()
						.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
						.collect(Collectors.joining("; "));
		return ResponseEntity.badRequest().body(new ErrorResponse(message));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
		return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
		return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(LobbyNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleLobbyNotFound(LobbyNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler({LobbyFullException.class, LobbyAlreadyStartedException.class})
	public ResponseEntity<ErrorResponse> handleLobbyConflict(RuntimeException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(ForbiddenActionException.class)
	public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenActionException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
	}
}
