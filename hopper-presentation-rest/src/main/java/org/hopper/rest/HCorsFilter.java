package org.hopper.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class HCorsFilter implements ContainerResponseFilter {

  private static final HRest hopperRest = HRest.getInstance();

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    if (hopperRest.isCorsAllowOrigin()) {
      responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
    }
  }
}
