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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hopper.core.draw.DrawnItem;

/**
 * Authoring-time description of a hit target a component supports for {@link HInteraction}
 * locations. Not persisted; used by the interaction builder UI.
 *
 * <p>{@link #itemType} / {@link #itemCategory} must match values written into {@link DrawnItem}s at
 * render time so {@link HInteractionLocation#matches} succeeds.
 */
@Getter
@Setter
@NoArgsConstructor
public class HInteractionLocationOption {

  /** Stable key for the option (e.g. {@code slice}, {@code cell}). */
  private String id;

  /** Human-readable label for the builder UI (e.g. "Pie slice"). */
  private String label;

  /** {@link DrawnItem.DrawnItemType} name: {@code Component} or {@code ComponentItem}. */
  private String itemType;

  /** {@link DrawnItem.Category} name, or null when any category is acceptable. */
  private String itemCategory;

  /** Default dimension column names used when matching {@link DrawnItem} context. */
  private List<String> dimensionColumns = new ArrayList<>();

  /** When true, the builder may refine {@link #dimensionColumns}. */
  private boolean dimensionsEditable;

  public HInteractionLocationOption(
      String id,
      String label,
      String itemType,
      String itemCategory,
      List<String> dimensionColumns,
      boolean dimensionsEditable) {
    this.id = id;
    this.label = label;
    this.itemType = itemType;
    this.itemCategory = itemCategory;
    this.dimensionColumns =
        dimensionColumns != null ? new ArrayList<>(dimensionColumns) : new ArrayList<>();
    this.dimensionsEditable = dimensionsEditable;
  }

  public static HInteractionLocationOption of(
      String id,
      String label,
      DrawnItem.DrawnItemType itemType,
      DrawnItem.Category category,
      List<String> dimensionColumns,
      boolean dimensionsEditable) {
    return new HInteractionLocationOption(
        id,
        label,
        itemType != null ? itemType.name() : null,
        category != null ? category.name() : null,
        dimensionColumns,
        dimensionsEditable);
  }

  public static HInteractionLocationOption item(
      String id,
      String label,
      DrawnItem.Category category,
      List<String> dimensionColumns,
      boolean dimensionsEditable) {
    return of(
        id,
        label,
        DrawnItem.DrawnItemType.ComponentItem,
        category,
        dimensionColumns,
        dimensionsEditable);
  }

  public static HInteractionLocationOption item(
      String id, String label, DrawnItem.Category category) {
    return item(id, label, category, List.of(), false);
  }

  public static HInteractionLocationOption wholeComponent() {
    return of(
        "whole",
        "Whole component",
        DrawnItem.DrawnItemType.Component,
        null,
        List.of(),
        false);
  }

  /** Convenience when dimensions are a fixed array. */
  public static HInteractionLocationOption item(
      String id,
      String label,
      DrawnItem.Category category,
      boolean dimensionsEditable,
      String... dimensionColumns) {
    return item(
        id,
        label,
        category,
        dimensionColumns != null ? Arrays.asList(dimensionColumns) : List.of(),
        dimensionsEditable);
  }
}
