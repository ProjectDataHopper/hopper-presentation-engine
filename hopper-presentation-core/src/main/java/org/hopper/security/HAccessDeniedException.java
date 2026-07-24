package org.hopper.security;

import org.hopper.core.exception.HException;

/** Thrown when a principal is not allowed to perform an action. */
public class HAccessDeniedException extends HException {
  private static final long serialVersionUID = 1L;

  private final HAction action;
  private final HResourceRef resource;

  public HAccessDeniedException(String message) {
    super(message);
    this.action = null;
    this.resource = null;
  }

  public HAccessDeniedException(HPrincipal principal, HAction action, HResourceRef resource) {
    super(buildMessage(principal, action, resource));
    this.action = action;
    this.resource = resource;
  }

  public HAction getAction() {
    return action;
  }

  public HResourceRef getResource() {
    return resource;
  }

  private static String buildMessage(
      HPrincipal principal, HAction action, HResourceRef resource) {
    String user = principal == null ? "anonymous" : principal.getUsername();
    String act = action == null ? "?" : action.code();
    if (resource == null || resource.getName() == null) {
      return "Access denied for user '" + user + "' action '" + act + "'";
    }
    return "Access denied for user '"
        + user
        + "' action '"
        + act
        + "' on "
        + resource;
  }
}
