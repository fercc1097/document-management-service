package com.clara.ops.challenge.document_management_service_challenge.web.error;

import com.clara.ops.challenge.document_management_service_challenge.exception.*;
import java.util.*;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DocumentNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(DocumentNotFoundException e) {
    return build(HttpStatus.NOT_FOUND, List.of(e.getMessage()));
  }

  @ExceptionHandler(DocumentNotReadyException.class)
  public ResponseEntity<ErrorResponse> notReady(DocumentNotReadyException e) {
    return build(HttpStatus.CONFLICT, List.of(e.getMessage()));
  }

  // Only our own client-input exception maps to 400 here. IllegalArgumentException is
  // deliberately NOT caught: a broad mapping would mask internal server faults (any library
  // or programming IAE thrown deep in the stack) as client errors. Malformed client inputs
  // are converted explicitly at the edge (bad UUID -> MethodArgumentTypeMismatchException,
  // bad sort direction -> InvalidDocumentException).
  @ExceptionHandler(InvalidDocumentException.class)
  public ResponseEntity<ErrorResponse> invalid(InvalidDocumentException e) {
    return build(HttpStatus.BAD_REQUEST, List.of(e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
    List<String> msgs =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .toList();
    return build(HttpStatus.BAD_REQUEST, msgs);
  }

  @ExceptionHandler({MethodArgumentTypeMismatchException.class, PropertyReferenceException.class})
  public ResponseEntity<ErrorResponse> badParams(Exception e) {
    return build(HttpStatus.BAD_REQUEST, List.of(e.getMessage()));
  }

  @ExceptionHandler(StorageException.class)
  public ResponseEntity<ErrorResponse> storage(StorageException e) {
    return build(HttpStatus.INTERNAL_SERVER_ERROR, List.of("Storage error"));
  }

  private ResponseEntity<ErrorResponse> build(HttpStatus status, List<String> messages) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse(status.value(), status.getReasonPhrase(), messages));
  }
}
