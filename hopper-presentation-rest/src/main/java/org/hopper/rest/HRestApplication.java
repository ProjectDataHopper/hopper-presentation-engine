package org.hopper.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath("api")
public class HRestApplication extends Application {
  public HRestApplication() {
    // Initialize the singleton up-front
    HRest.getInstance();
  }
}
