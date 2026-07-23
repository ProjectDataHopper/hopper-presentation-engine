package org.hopper.rest.resources;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.hop.core.Const;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.presentation.HPresentation;
import org.hopper.rest.HRest;
import org.hopper.rest.history.PresentationSnapshot;
import org.hopper.rest.history.PresentationUndoService;

public abstract class BaseResource {
  protected final HRest hopperRest = HRest.getInstance();
  protected final PresentationUndoService undoService = PresentationUndoService.getInstance();

  protected Response getServerError(String errorMessage) {
    return getServerError(errorMessage, null, true);
  }

  protected Response getServerError(String errorMessage, boolean logOnServer) {
    return getServerError(errorMessage, null, logOnServer);
  }

  protected Response getServerError(String errorMessage, Exception e) {
    return getServerError(errorMessage, e, true);
  }

  protected Response getServerError(String errorMessage, Exception e, boolean logOnServer) {
    if (logOnServer) {
      if (e != null) {
        hopperRest.getLog().logError(errorMessage, e);
      } else {
        hopperRest.getLog().logError(errorMessage);
      }
    }
    return Response.serverError()
        .status(Response.Status.INTERNAL_SERVER_ERROR)
        .entity(errorMessage + (e == null ? "" : ("\n" + Const.getSimpleStackTrace(e))))
        .type(MediaType.TEXT_PLAIN)
        .build();
  }

  /**
   * Snapshot presentation JSON for undo (call only after a successful save). Failures are logged
   * and swallowed so undo never breaks the primary mutation path.
   */
  protected void recordPresentationUndo(String presentationName, String beforeJson) {
    try {
      if (beforeJson != null && presentationName != null) {
        undoService.record(presentationName, beforeJson);
      }
    } catch (Exception e) {
      hopperRest.getLog().logError("Failed to record undo snapshot for '" + presentationName + "'", e);
    }
  }

  protected String snapshotPresentation(HPresentation presentation) {
    try {
      return PresentationSnapshot.toJson(presentation, hopperRest.getMetadataProvider());
    } catch (Exception e) {
      hopperRest
          .getLog()
          .logError(
              "Failed to snapshot presentation '"
                  + (presentation != null ? presentation.getName() : "?")
                  + "'",
              e);
      return null;
    }
  }

  protected String snapshotPresentationByName(String name) {
    try {
      IHopMetadataProvider provider = hopperRest.getMetadataProvider();
      return PresentationSnapshot.loadJson(name, provider);
    } catch (Exception e) {
      hopperRest.getLog().logError("Failed to snapshot presentation '" + name + "'", e);
      return null;
    }
  }
}
