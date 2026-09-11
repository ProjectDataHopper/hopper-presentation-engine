package org.hopper.presentation.simple;

import lombok.Getter;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.chart.HGanttChartComponent;
import org.hopper.presentation.component.types.chart.HLineChartComponent;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.connector.types.memory.HInMemoryRowsConnector;
import org.hopper.presentation.page.HPage;

/**
 * Isolated presentation catalog produced by {@link HSimplePresentation}: themes, in-memory
 * connectors, and the generated presentation. Not part of Hop GUI project metadata.
 */
@Getter
public final class HGeneratedCatalog {

  private final IHopMetadataProvider provider;
  private final HPresentation presentation;

  public HGeneratedCatalog(IHopMetadataProvider provider, HPresentation presentation) {
    this.provider = provider;
    this.presentation = presentation;
  }

  /** First in-memory row connector in this catalog, or {@code null}. */
  public HInMemoryRowsConnector findInMemoryConnector() {
    if (provider == null) {
      return null;
    }
    try {
      var serializer = provider.getSerializer(HConnector.class);
      for (String name : serializer.listObjectNames()) {
        HConnector connector = serializer.load(name);
        if (connector != null && connector.getConnector() instanceof HInMemoryRowsConnector rows) {
          return rows;
        }
      }
    } catch (Exception ignored) {
      return null;
    }
    return null;
  }

  /** First line chart on the presentation, or {@code null}. */
  public HLineChartComponent findLineChartComponent() {
    if (presentation == null || presentation.getPages() == null) {
      return null;
    }
    for (HPage page : presentation.getPages()) {
      if (page == null || page.getComponents() == null) {
        continue;
      }
      for (HComponent component : page.getComponents()) {
        if (component != null && component.getComponent() instanceof HLineChartComponent line) {
          return line;
        }
      }
    }
    return null;
  }

  /** First Gantt component on the presentation, or {@code null}. */
  public HGanttChartComponent findGanttComponent() {
    if (presentation == null || presentation.getPages() == null) {
      return null;
    }
    for (HPage page : presentation.getPages()) {
      if (page == null || page.getComponents() == null) {
        continue;
      }
      for (HComponent component : page.getComponents()) {
        if (component != null && component.getComponent() instanceof HGanttChartComponent gantt) {
          return gantt;
        }
      }
    }
    return null;
  }
}
