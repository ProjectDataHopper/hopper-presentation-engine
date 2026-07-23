package org.hopper.core.exception;

public class HException extends Exception {
  private static final long serialVersionUID = -2472634745866870891L;

  public HException() {
    super();
  }

  public HException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  public HException(String message, Throwable cause) {
    super(message, cause);
  }

  public HException(String message) {
    super(message);
  }

  public HException(Throwable cause) {
    super(cause);
  }
}
