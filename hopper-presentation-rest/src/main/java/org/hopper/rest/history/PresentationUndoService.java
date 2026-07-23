package org.hopper.rest.history;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;

/**
 * In-memory undo/redo stacks of full presentation JSON snapshots (per presentation name).
 *
 * <p>Not persisted across restarts. Snapshots are recorded after a successful mutate so failed
 * saves never pollute history. A new mutation clears the redo stack.
 */
public final class PresentationUndoService {

  public static final int DEFAULT_MAX_DEPTH = 50;

  private static final PresentationUndoService INSTANCE = new PresentationUndoService();

  private final int maxDepth;
  private final ConcurrentHashMap<String, UndoState> byName = new ConcurrentHashMap<>();

  public PresentationUndoService() {
    this(DEFAULT_MAX_DEPTH);
  }

  public PresentationUndoService(int maxDepth) {
    this.maxDepth = Math.max(1, maxDepth);
  }

  public static PresentationUndoService getInstance() {
    return INSTANCE;
  }

  /**
   * Record a pre-change snapshot after a successful save. Clears redo for this presentation.
   *
   * @param presentationName metadata name
   * @param beforeJson full presentation JSON as it was before the mutation
   */
  public void record(String presentationName, String beforeJson) {
    if (StringUtils.isBlank(presentationName) || StringUtils.isBlank(beforeJson)) {
      return;
    }
    UndoState state = byName.computeIfAbsent(presentationName, n -> new UndoState());
    synchronized (state) {
      state.undo.push(beforeJson);
      while (state.undo.size() > maxDepth) {
        state.undo.removeLast();
      }
      state.redo.clear();
    }
  }

  /**
   * Pop undo snapshot and push current JSON onto redo.
   *
   * @return previous JSON to restore, or null if nothing to undo
   */
  public String undo(String presentationName, String currentJson) {
    if (StringUtils.isBlank(presentationName)) {
      return null;
    }
    UndoState state = byName.get(presentationName);
    if (state == null) {
      return null;
    }
    synchronized (state) {
      if (state.undo.isEmpty()) {
        return null;
      }
      String previous = state.undo.pop();
      if (StringUtils.isNotBlank(currentJson)) {
        state.redo.push(currentJson);
        while (state.redo.size() > maxDepth) {
          state.redo.removeLast();
        }
      }
      return previous;
    }
  }

  /**
   * Pop redo snapshot and push current JSON onto undo.
   *
   * @return JSON to restore, or null if nothing to redo
   */
  public String redo(String presentationName, String currentJson) {
    if (StringUtils.isBlank(presentationName)) {
      return null;
    }
    UndoState state = byName.get(presentationName);
    if (state == null) {
      return null;
    }
    synchronized (state) {
      if (state.redo.isEmpty()) {
        return null;
      }
      String next = state.redo.pop();
      if (StringUtils.isNotBlank(currentJson)) {
        state.undo.push(currentJson);
        while (state.undo.size() > maxDepth) {
          state.undo.removeLast();
        }
      }
      return next;
    }
  }

  public Map<String, Object> status(String presentationName) {
    Map<String, Object> out = new LinkedHashMap<>();
    UndoState state =
        StringUtils.isBlank(presentationName) ? null : byName.get(presentationName);
    int undoDepth = 0;
    int redoDepth = 0;
    if (state != null) {
      synchronized (state) {
        undoDepth = state.undo.size();
        redoDepth = state.redo.size();
      }
    }
    out.put("canUndo", undoDepth > 0);
    out.put("canRedo", redoDepth > 0);
    out.put("undoDepth", undoDepth);
    out.put("redoDepth", redoDepth);
    return out;
  }

  /** Drop stacks when a presentation is renamed or deleted. */
  public void clear(String presentationName) {
    if (StringUtils.isBlank(presentationName)) {
      return;
    }
    byName.remove(presentationName);
  }

  /** Move stacks from old name to new name (rename). */
  public void rename(String oldName, String newName) {
    if (StringUtils.isBlank(oldName) || StringUtils.isBlank(newName) || oldName.equals(newName)) {
      return;
    }
    UndoState state = byName.remove(oldName);
    if (state != null) {
      byName.put(newName, state);
    }
  }

  private static final class UndoState {
    /** Most recent change is at the head (pop first). */
    final Deque<String> undo = new ArrayDeque<>();
    final Deque<String> redo = new ArrayDeque<>();
  }
}
