package com.sysconf.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

	@ExceptionHandler(RtaUploadValidationException.class)
	public ResponseEntity<Map<String, Object>> handleRtaUploadValidation(RtaUploadValidationException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("result", "FAIL");
		body.put("message", ex.getMessage());
		return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(GuildBattleRecordAccessException.class)
	public ResponseEntity<Map<String, Object>> handleGuildBattleRecordAccess(GuildBattleRecordAccessException ex) {
		Map<String, Object> body = new HashMap<>();
		body.put("result", "FAIL");
		body.put("message", ex.getMessage());
		return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
	}
}
