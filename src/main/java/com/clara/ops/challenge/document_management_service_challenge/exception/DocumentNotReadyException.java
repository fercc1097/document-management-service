package com.clara.ops.challenge.document_management_service_challenge.exception;

public class DocumentNotReadyException extends RuntimeException {
  public DocumentNotReadyException(String message) {
    super(message);
  }
}
