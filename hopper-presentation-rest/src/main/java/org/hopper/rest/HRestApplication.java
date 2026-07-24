package org.hopper.rest;

import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * JAX-RS application. Extends Jersey {@link ResourceConfig} so resources/providers are registered
 * explicitly (works on Jetty and plain Tomcat; avoids empty Application scan quirks).
 *
 * <p>Context path is {@code /hopper} (Tomcat hopper.war / jetty contextPath). Application path is
 * {@code api}, so endpoints are {@code /hopper/api/...}.
 */
@ApplicationPath("api")
public class HRestApplication extends ResourceConfig {

  public HRestApplication() {
    // Explicit package scan for REST resources, filters (@Provider), and plugin info REST
    packages(
        "org.hopper.rest.resources",
        "org.hopper.rest.security",
        "org.hopper.core.plugin");
    register(JacksonFeature.class);

    // Initialize the singleton up-front (config, metadata, auth)
    HRest.getInstance();
  }
}
