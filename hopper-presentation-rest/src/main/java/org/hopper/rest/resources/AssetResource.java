package org.hopper.rest.resources;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import java.io.File;
import org.apache.commons.lang3.StringUtils;

/**
 * REST endpoint serving generated image assets stored under the presentation metadata catalog path
 * (e.g. `${HOPPER_METADATA_PATH}/assets/...`).
 */
@Path("assets/")
public class AssetResource extends BaseResource {

  @GET
  @Path("{presentationName}/{assetPath:.+}")
  public Response getAsset(
      @PathParam("presentationName") String presentationName,
      @PathParam("assetPath") String assetPath) {
    try {
      if (StringUtils.isBlank(presentationName) || StringUtils.isBlank(assetPath)) {
        return Response.status(Response.Status.BAD_REQUEST).entity("Invalid asset request").build();
      }

      String metadataPath = hopperRest.getMetadataPath();
      if (StringUtils.isBlank(metadataPath)) {
        metadataPath = System.getProperty("HOPPER_METADATA_PATH");
      }
      if (StringUtils.isBlank(metadataPath)) {
        return Response.status(Response.Status.NOT_FOUND).entity("Metadata path not set").build();
      }

      File assetFile = new File(metadataPath, "assets" + File.separator + presentationName + File.separator + assetPath);
      if (!assetFile.exists() || !assetFile.isFile()) {
        return Response.status(Response.Status.NOT_FOUND).entity("Asset not found").build();
      }

      String contentType = getContentType(assetFile.getName());
      return Response.ok(assetFile).type(contentType).build();

    } catch (Exception e) {
      return getServerError("Error fetching asset", e);
    }
  }

  private String getContentType(String fileName) {
    String lower = fileName.toLowerCase();
    if (lower.endsWith(".png")) {
      return "image/png";
    } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (lower.endsWith(".svg")) {
      return "image/svg+xml";
    } else if (lower.endsWith(".webp")) {
      return "image/webp";
    }
    return "application/octet-stream";
  }
}
