package org.hopper.rest.security;

/** Failed Bearer token authentication (missing, expired, bad signature, claim mismatch). */
public class OAuth2AuthenticationException extends Exception {
  private static final long serialVersionUID = 1L;

  public OAuth2AuthenticationException(String message) {
    super(message);
  }

  public OAuth2AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
