package org.hopper.core.draw;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hopper.core.HGeometry;

// TODO: support Rotation of a drawn item, push up HGeometry.contains()
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = {
  "componentName",
  "componentPluginId",
  "partNumber",
  "type",
  "category",
  "rowNr",
  "colNr"
})
public class DrawnItem {

  private String componentName;
  private String componentPluginId;
  private int partNumber;
  private DrawnItemType type;
  private String category;
  private int rowNr;
  private int colNr;
  private HGeometry geometry;
  private DrawnContext context;

  public DrawnItem(
      String componentName,
      String componentPluginId,
      int partNumber,
      DrawnItemType type,
      String category,
      int rowNr,
      int colNr,
      HGeometry geometry,
      DrawnContext context) {
    this.componentName = componentName;
    this.componentPluginId = componentPluginId;
    this.partNumber = partNumber;
    this.type = type;
    this.category = category;
    this.rowNr = rowNr;
    this.colNr = colNr;
    this.geometry = geometry;
    this.context = context;
  }

  public DrawnItem(
      String componentName,
      String componentPluginId,
      int partNumber,
      DrawnItemType type,
      String category,
      int rowNr,
      int colNr,
      HGeometry geometry) {
    this(
        componentName, componentPluginId, partNumber, type, category, rowNr, colNr, geometry, null);
  }

  @Override
  public String toString() {
    String string =
        "DrawnItem{"
            + "componentName='"
            + componentName
            + '\''
            + ", componentPluginId='"
            + componentPluginId
            + '\''
            + ", partNumber="
            + partNumber
            + ", type="
            + type.name()
            + ", category='"
            + category
            + '\''
            + ", rowNr="
            + rowNr
            + ", colNr="
            + colNr
            + ", geometry="
            + geometry;

    if (context != null) {
      string += ", context=" + context;
    }
    string += '}';
    return string;
  }

  public String toJsonString() throws JsonProcessingException {
    return new ObjectMapper().writeValueAsString(this);
  }

  public enum DrawnItemType {
    Component,
    ComponentItem,
  }

  public enum Category {
    ComponentArea,
    Label,
    Cell,
    Line,
    Header,
    Title,
    LegendTitle,
    LegendEntry,
    XAxisLabel,
    YAxisLabel,
    ChartSeriesLabel,
    ChartLabel,
  }
}
