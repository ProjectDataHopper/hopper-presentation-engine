package org.hopper.presentation.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.hopper.presentation.component.types.chart.HBaseChartComponent;
import org.hopper.presentation.component.types.crosstab.HBaseAggregatingComponent;

/**
 * Serializable-enough snapshot of aggregating/chart runtime fields filled by {@code
 * processSourceData}, so layout cache hits can skip re-pivoting.
 */
public final class HAggregatingRuntimeState {

  private List<Integer> horizontalDimensionIndexes;
  private List<Integer> verticalDimensionIndexes;
  private List<Integer> factIndexes;
  private List<IValueMeta> horizontalDimensionValueMetas;
  private List<IValueMeta> verticalDimensionValueMetas;
  private List<Map<List<String>, Object>> pivotMapList;
  private List<Map<List<String>, Long>> countMapList;
  private IRowMeta inputRowMeta;
  private String titleText;
  private int actualHorizontalLabelInterval;
  private boolean usingTotalHeights;

  public static HAggregatingRuntimeState capture(HBaseAggregatingComponent c) {
    if (c == null) {
      return null;
    }
    HAggregatingRuntimeState s = new HAggregatingRuntimeState();
    s.horizontalDimensionIndexes = copyIntList(c.getHorizontalDimensionIndexes());
    s.verticalDimensionIndexes = copyIntList(c.getVerticalDimensionIndexes());
    s.factIndexes = copyIntList(c.getFactIndexes());
    s.horizontalDimensionValueMetas = copyValueMetas(c.getHorizontalDimensionValueMetas());
    s.verticalDimensionValueMetas = copyValueMetas(c.getVerticalDimensionValueMetas());
    s.pivotMapList = copyPivotMaps(c.getPivotMapList());
    s.countMapList = copyCountMaps(c.getCountMapList());
    s.inputRowMeta = cloneRowMeta(c.getInputRowMeta());
    if (c instanceof HBaseChartComponent chart) {
      s.titleText = chart.getTitleText();
      s.actualHorizontalLabelInterval = chart.getActualHorizontalLabelInterval();
      s.usingTotalHeights = chart.isUsingTotalHeights();
    }
    return s;
  }

  public void restoreOnto(HBaseAggregatingComponent c) {
    if (c == null) {
      return;
    }
    c.setHorizontalDimensionIndexes(copyIntList(horizontalDimensionIndexes));
    c.setVerticalDimensionIndexes(copyIntList(verticalDimensionIndexes));
    c.setFactIndexes(copyIntList(factIndexes));
    c.setHorizontalDimensionValueMetas(copyValueMetas(horizontalDimensionValueMetas));
    c.setVerticalDimensionValueMetas(copyValueMetas(verticalDimensionValueMetas));
    c.setPivotMapList(copyPivotMaps(pivotMapList));
    c.setCountMapList(copyCountMaps(countMapList));
    c.setInputRowMeta(cloneRowMeta(inputRowMeta));
    if (c instanceof HBaseChartComponent chart) {
      chart.setTitleText(titleText);
      chart.setActualHorizontalLabelInterval(actualHorizontalLabelInterval);
      chart.setUsingTotalHeights(usingTotalHeights);
    }
  }

  private static List<Integer> copyIntList(List<Integer> src) {
    return src == null ? null : new ArrayList<>(src);
  }

  private static List<IValueMeta> copyValueMetas(List<IValueMeta> src) {
    if (src == null) {
      return null;
    }
    List<IValueMeta> out = new ArrayList<>(src.size());
    for (IValueMeta vm : src) {
      out.add(vm == null ? null : vm.clone());
    }
    return out;
  }

  private static List<Map<List<String>, Object>> copyPivotMaps(
      List<Map<List<String>, Object>> src) {
    if (src == null) {
      return null;
    }
    List<Map<List<String>, Object>> out = new ArrayList<>(src.size());
    for (Map<List<String>, Object> m : src) {
      if (m == null) {
        out.add(null);
        continue;
      }
      Map<List<String>, Object> copy = new HashMap<>();
      for (Map.Entry<List<String>, Object> e : m.entrySet()) {
        List<String> key = e.getKey() == null ? null : new ArrayList<>(e.getKey());
        copy.put(key, e.getValue());
      }
      out.add(copy);
    }
    return out;
  }

  private static List<Map<List<String>, Long>> copyCountMaps(List<Map<List<String>, Long>> src) {
    if (src == null) {
      return null;
    }
    List<Map<List<String>, Long>> out = new ArrayList<>(src.size());
    for (Map<List<String>, Long> m : src) {
      if (m == null) {
        out.add(null);
        continue;
      }
      Map<List<String>, Long> copy = new HashMap<>();
      for (Map.Entry<List<String>, Long> e : m.entrySet()) {
        List<String> key = e.getKey() == null ? null : new ArrayList<>(e.getKey());
        copy.put(key, e.getValue());
      }
      out.add(copy);
    }
    return out;
  }

  private static IRowMeta cloneRowMeta(IRowMeta meta) {
    if (meta == null) {
      return null;
    }
    try {
      Object c = meta.clone();
      if (c instanceof IRowMeta m) {
        return m;
      }
    } catch (Exception ignored) {
      // fall through
    }
    return meta;
  }
}
