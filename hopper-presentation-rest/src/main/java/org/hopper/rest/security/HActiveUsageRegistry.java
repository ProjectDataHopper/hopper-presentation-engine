package org.hopper.rest.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hopper.security.HPrincipal;

/**
 * In-memory registry of active presentation renders / usage for the admin “who is doing what”
 * view.
 */
public class HActiveUsageRegistry {

  private static final HActiveUsageRegistry INSTANCE = new HActiveUsageRegistry();

  private final Map<String, ActiveUsage> byRenderId = new ConcurrentHashMap<>();

  public static HActiveUsageRegistry getInstance() {
    return INSTANCE;
  }

  public void start(
      String renderId,
      String presentationName,
      HPrincipal principal,
      String requestId) {
    if (renderId == null || renderId.isBlank()) {
      return;
    }
    List<String> roles =
        principal != null && principal.getRoles() != null
            ? new ArrayList<>(principal.getRoles())
            : List.of();
    ActiveUsage usage =
        new ActiveUsage(
            renderId,
            presentationName,
            principal != null ? principal.getUsername() : "anonymous",
            principal != null ? principal.getSubject() : null,
            roles,
            requestId,
            Instant.now());
    byRenderId.put(renderId, usage);
  }

  public void end(String renderId) {
    if (renderId != null) {
      byRenderId.remove(renderId);
    }
  }

  /**
   * Presentation name last associated with a render UUID (may outlive the cache entry briefly).
   * Used to rebuild after TTL eviction when the client still holds the old render id.
   */
  public String presentationNameFor(String renderId) {
    if (renderId == null || renderId.isBlank()) {
      return null;
    }
    ActiveUsage u = byRenderId.get(renderId);
    return u != null ? u.presentationName() : null;
  }

  /**
   * Drop usage rows whose render id is no longer in the live render cache (keeps Live usage honest
   * after TTL/LRU purge).
   *
   * @param liveRenderIds currently cached render ids
   * @return number of stale usage rows removed
   */
  public int pruneNotIn(java.util.Set<String> liveRenderIds) {
    if (liveRenderIds == null) {
      liveRenderIds = java.util.Set.of();
    }
    int removed = 0;
    for (String id : new ArrayList<>(byRenderId.keySet())) {
      if (!liveRenderIds.contains(id)) {
        byRenderId.remove(id);
        removed++;
      }
    }
    return removed;
  }

  public List<ActiveUsage> listActive() {
    return new ArrayList<>(byRenderId.values());
  }

  public int size() {
    return byRenderId.size();
  }

  public void clear() {
    byRenderId.clear();
  }

  public record ActiveUsage(
      String renderId,
      String presentationName,
      String username,
      String subject,
      List<String> roles,
      String requestId,
      Instant startedAt) {

    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("renderId", renderId);
      map.put("presentationName", presentationName);
      map.put("username", username);
      map.put("subject", subject);
      map.put("roles", roles);
      map.put("requestId", requestId);
      map.put("startedAt", startedAt != null ? startedAt.toString() : null);
      if (startedAt != null) {
        map.put("ageMs", Math.max(0, Instant.now().toEpochMilli() - startedAt.toEpochMilli()));
      }
      return map;
    }
  }
}
