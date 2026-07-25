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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hopper.core.HColumn;
import org.hopper.core.HGeometry;
import org.hopper.core.draw.DrawnContext;
import org.hopper.core.draw.DrawnItem;
import org.junit.jupiter.api.Test;

/**
 * Pie slices register as {@link DrawnItem.Category#ChartLabel}, not Cell. A location saved with
 * Cell (e.g. table-drill preset) must not match a pie slice hit.
 */
class HInteractionLocationMatchTest {

  private static DrawnItem pieSlice(String componentName, String regionValue) {
    DrawnContext ctx =
        new DrawnContext(List.of(new HColumn("region")), regionValue);
    return new DrawnItem(
        componentName,
        "HPieChartComponent",
        0,
        DrawnItem.DrawnItemType.ComponentItem,
        DrawnItem.Category.ChartLabel.name(),
        0,
        0,
        new HGeometry(10, 10, 40, 40),
        ctx);
  }

  @Test
  void pieSliceDoesNotMatchCellCategory() {
    HInteractionLocation loc =
        new HInteractionLocation(
            "TopRighttPie",
            "HPieChartComponent",
            DrawnItem.DrawnItemType.ComponentItem.name(),
            DrawnItem.Category.Cell.name(),
            List.of("region"));
    assertFalse(loc.matches(pieSlice("TopRighttPie", "EMEA")));
  }

  @Test
  void pieSliceMatchesChartLabelWithRegionDimension() {
    HInteractionLocation loc =
        new HInteractionLocation(
            "TopRighttPie",
            "HPieChartComponent",
            DrawnItem.DrawnItemType.ComponentItem.name(),
            DrawnItem.Category.ChartLabel.name(),
            List.of("region"));
    assertTrue(loc.matches(pieSlice("TopRighttPie", "EMEA")));
  }

  @Test
  void pieSliceDoesNotMatchWrongComponentName() {
    HInteractionLocation loc =
        new HInteractionLocation(
            "LeftPie",
            "HPieChartComponent",
            DrawnItem.DrawnItemType.ComponentItem.name(),
            DrawnItem.Category.ChartLabel.name(),
            List.of());
    assertFalse(loc.matches(pieSlice("TopRighttPie", "EMEA")));
  }
}
