package org.hopper.presentation.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hopper.core.HDataSet;
import org.hopper.core.HGeometry;
import org.hopper.presentation.HComponentLayoutResult;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.type.IHComponent;
import org.hopper.presentation.component.types.crosstab.HBaseAggregatingComponent;
import org.hopper.presentation.page.HPage;

/**
 * Cached layout output for a single-part component (v1: no multi-page). Includes aggregating
 * runtime state so processSourceData can be skipped on hit.
 */
public final class HComponentLayoutSnapshot {

  private final String fingerprint;
  private final String dataFingerprint;
  private final HGeometry firstGeometry;
  private final HGeometry lastGeometry;
  private final int partNumber;
  private final Map<String, Object> dataMap;
  private final HDataSet dataSet;
  /**
   * Values stored via {@link HLayoutResults#addDataSet} (e.g. SVG {@code SvgDetails}). Keyed by
   * dataset name; restored on replay so render does not NPE after a layout-cache hit.
   */
  private final Map<String, Object> resultsDataSets;
  private final HAggregatingRuntimeState aggregatingState;
  private final long createdAtNanos;

  public HComponentLayoutSnapshot(
      String fingerprint,
      String dataFingerprint,
      HGeometry firstGeometry,
      HGeometry lastGeometry,
      int partNumber,
      Map<String, Object> dataMap,
      HDataSet dataSet,
      Map<String, Object> resultsDataSets,
      HAggregatingRuntimeState aggregatingState) {
    this.fingerprint = fingerprint;
    this.dataFingerprint = dataFingerprint;
    this.firstGeometry = firstGeometry == null ? null : copyGeo(firstGeometry);
    this.lastGeometry = lastGeometry == null ? null : copyGeo(lastGeometry);
    this.partNumber = partNumber;
    this.dataMap = copyDataMap(dataMap);
    this.dataSet = dataSet;
    this.resultsDataSets = copyDataMap(resultsDataSets);
    this.aggregatingState = aggregatingState;
    this.createdAtNanos = System.nanoTime();
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getDataFingerprint() {
    return dataFingerprint;
  }

  public long getCreatedAtNanos() {
    return createdAtNanos;
  }

  public HGeometry getFirstGeometry() {
    return firstGeometry;
  }

  /**
   * Inject this snapshot into a fresh layout pass: restore plugin runtime state, register
   * geometry, add layout result on the current first body page for {@code page}.
   */
  public void replay(HLayoutResults results, HPage page, HComponent hopperComponent) {
    if (results == null || hopperComponent == null) {
      return;
    }
    IHComponent plugin = hopperComponent.getComponent();
    if (aggregatingState != null && plugin instanceof HBaseAggregatingComponent agg) {
      aggregatingState.restoreOnto(agg);
    }

    // Restore layout-results bags (SVG details, etc.) before render
    if (resultsDataSets != null && !resultsDataSets.isEmpty()) {
      for (Map.Entry<String, Object> e : resultsDataSets.entrySet()) {
        if (e.getKey() != null) {
          results.addDataSet(hopperComponent, e.getKey(), e.getValue());
        }
      }
    }

    HGeometry geo = lastGeometry != null ? copyGeo(lastGeometry) : copyGeo(firstGeometry);
    if (geo != null) {
      results.addComponentGeometry(hopperComponent.getName(), geo);
    }

    HRenderPage renderPage = results.getFirstRenderPage(page);
    if (renderPage == null) {
      renderPage = results.addNewPage(page, null);
    }

    HComponentLayoutResult result = new HComponentLayoutResult();
    result.setComponent(hopperComponent);
    result.setSourcePage(page);
    result.setRenderPage(renderPage);
    result.setPartNumber(partNumber > 0 ? partNumber : 1);
    result.setGeometry(geo);
    result.setDataMap(copyDataMap(dataMap));
    result.setDataSet(dataSet);
    if (renderPage.getLayoutResults() == null) {
      renderPage.setLayoutResults(new ArrayList<>());
    }
    renderPage.getLayoutResults().add(result);
  }

  /**
   * Capture a single-part layout result for the named component from {@code results}. Returns null
   * if multi-part or missing.
   */
  public static HComponentLayoutSnapshot capture(
      String fingerprint,
      String dataFingerprint,
      HLayoutResults results,
      HComponent hopperComponent,
      List<HComponentLayoutResult> partsOnThisLayout) {
    if (hopperComponent == null || partsOnThisLayout == null || partsOnThisLayout.isEmpty()) {
      return null;
    }
    // v1: single part only
    if (partsOnThisLayout.size() != 1) {
      return null;
    }
    HComponentLayoutResult part = partsOnThisLayout.get(0);
    if (part == null || part.getGeometry() == null) {
      return null;
    }
    HGeometry first =
        results != null ? results.findFirstGeometry(hopperComponent.getName()) : part.getGeometry();
    HGeometry last =
        results != null ? results.findGeometry(hopperComponent.getName()) : part.getGeometry();

    HAggregatingRuntimeState aggState = null;
    IHComponent plugin = hopperComponent.getComponent();
    if (plugin instanceof HBaseAggregatingComponent agg) {
      aggState = HAggregatingRuntimeState.capture(agg);
    }

    Map<String, Object> resultsDataSets = null;
    if (results != null && results.getComponentDataSetMap() != null) {
      Map<String, Object> bag = results.getComponentDataSetMap().get(hopperComponent.getName());
      if (bag != null && !bag.isEmpty()) {
        resultsDataSets = new HashMap<>(bag);
      }
    }

    return new HComponentLayoutSnapshot(
        fingerprint,
        dataFingerprint,
        first,
        last,
        part.getPartNumber(),
        part.getDataMap(),
        part.getDataSet(),
        resultsDataSets,
        aggState);
  }

  private static HGeometry copyGeo(HGeometry g) {
    if (g == null) {
      return null;
    }
    return new HGeometry(g.getX(), g.getY(), g.getWidth(), g.getHeight());
  }

  private static Map<String, Object> copyDataMap(Map<String, Object> src) {
    if (src == null) {
      return new HashMap<>();
    }
    // Shallow copy of keys; values expected to be immutable primitives / simple objects
    return new HashMap<>(src);
  }
}
