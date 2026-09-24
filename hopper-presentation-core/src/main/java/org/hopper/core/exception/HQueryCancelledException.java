package org.hopper.core.exception;

/** The user cancelled a presentation query before it finished. */
public class HQueryCancelledException extends HException {

  public HQueryCancelledException() {
    super("Query cancelled");
  }
}
