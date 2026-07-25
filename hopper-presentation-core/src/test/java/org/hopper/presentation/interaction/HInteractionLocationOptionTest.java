/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hopper.presentation.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.core.HColumn;
import org.hopper.core.HDimension;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.component.types.chart.HBarChartComponent;
import org.hopper.presentation.component.types.chart.HGanttChartComponent;
import org.hopper.presentation.component.types.chart.HPieChartComponent;
import org.hopper.presentation.component.types.image.HImageComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.component.types.table.HTableComponent;
import org.junit.jupiter.api.Test;

class HInteractionLocationOptionTest {

  @Test
  void wholeComponentOptionUsesComponentType() {
    HInteractionLocationOption whole = HInteractionLocationOption.wholeComponent();
    assertEquals("whole", whole.getId());
    assertEquals(DrawnItem.DrawnItemType.Component.name(), whole.getItemType());
    assertFalse(whole.isDimensionsEditable());
  }

  @Test
  void pieChartDeclaresSliceLegendAndTitle() {
    HPieChartComponent pie = new HPieChartComponent();
    pie.getHorizontalDimensions().add(new HDimension("Country"));
    List<HInteractionLocationOption> options = pie.getPossibleInteractionLocations();
    assertEquals(3, options.size());
    assertEquals("slice", options.get(0).getId());
    assertEquals(DrawnItem.Category.ChartLabel.name(), options.get(0).getItemCategory());
    assertEquals(List.of("Country"), options.get(0).getDimensionColumns());
    assertTrue(options.get(0).isDimensionsEditable());
    assertEquals("legend", options.get(1).getId());
    assertEquals(DrawnItem.Category.LegendEntry.name(), options.get(1).getItemCategory());
    assertEquals("title", options.get(2).getId());
  }

  @Test
  void tableDeclaresCellAndHeaderWithColumns() {
    HTableComponent table = new HTableComponent();
    table.getColumnSelection().add(new HColumn("REGION"));
    table.getColumnSelection().add(new HColumn("SALES"));
    List<HInteractionLocationOption> options = table.getPossibleInteractionLocations();
    assertEquals(2, options.size());
    assertEquals("cell", options.get(0).getId());
    assertEquals(DrawnItem.Category.Cell.name(), options.get(0).getItemCategory());
    assertEquals(List.of("REGION", "SALES"), options.get(0).getDimensionColumns());
    assertEquals("header", options.get(1).getId());
  }

  @Test
  void labelDeclaresLabelTextLocation() {
    HLabelComponent label = new HLabelComponent();
    List<HInteractionLocationOption> options = label.getPossibleInteractionLocations();
    assertEquals(1, options.size());
    assertEquals("label", options.get(0).getId());
    assertEquals(DrawnItem.Category.Label.name(), options.get(0).getItemCategory());
  }

  @Test
  void barChartDeclaresBarCategoryAndAxes() {
    HBarChartComponent bar = new HBarChartComponent();
    bar.getHorizontalDimensions().add(new HDimension("Category"));
    List<HInteractionLocationOption> options = bar.getPossibleInteractionLocations();
    assertTrue(options.size() >= 4);
    assertEquals("bar", options.get(0).getId());
    assertEquals(DrawnItem.Category.ChartLabel.name(), options.get(0).getItemCategory());
    assertEquals(List.of("Category"), options.get(0).getDimensionColumns());
  }

  @Test
  void ganttDeclaresBarWithTaskColumn() {
    HGanttChartComponent gantt = new HGanttChartComponent();
    gantt.setTaskColumn("task");
    gantt.setShowingTitle(true);
    gantt.setTitle("Timeline");
    List<HInteractionLocationOption> options = gantt.getPossibleInteractionLocations();
    assertEquals(2, options.size());
    assertEquals("bar", options.get(0).getId());
    assertEquals(DrawnItem.Category.GanttBar.name(), options.get(0).getItemCategory());
    assertEquals(List.of("task"), options.get(0).getDimensionColumns());
    assertEquals("title", options.get(1).getId());
  }

  @Test
  void imageHasNoComponentSpecificLocations() {
    HImageComponent image = new HImageComponent();
    assertTrue(image.getPossibleInteractionLocations().isEmpty());
  }
}
