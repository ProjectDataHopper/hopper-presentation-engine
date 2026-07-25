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
package org.hopper.render.pdf;

import org.hopper.presentation.page.HPage;

/**
 * Paper size presets for PDF export (CSS px at 96dpi-ish, matching {@link HPage#getA4(boolean)}).
 */
public final class HPdfPaper {

  public static final int MARGIN_DEFAULT = 25;

  private HPdfPaper() {}

  /**
   * Resolve a body page size from a preset name.
   *
   * @param preset {@code a4}, {@code letter}, {@code legal}, {@code a3}, or {@code custom}
   * @param portrait true for portrait; false for landscape
   * @param customWidth required when preset is custom
   * @param customHeight required when preset is custom
   * @param margin uniform margin when &gt; 0; otherwise {@link #MARGIN_DEFAULT}
   */
  public static HPage toPage(
      String preset,
      boolean portrait,
      Integer customWidth,
      Integer customHeight,
      Integer margin) {
    int m = margin != null && margin >= 0 ? margin : MARGIN_DEFAULT;
    String p = preset != null ? preset.trim().toLowerCase() : "a4";
    int w;
    int h;
    switch (p) {
      case "letter":
        w = 816;
        h = 1056;
        break;
      case "legal":
        w = 816;
        h = 1344;
        break;
      case "a3":
        w = 1123;
        h = 1587;
        break;
      case "custom":
        w = customWidth != null && customWidth > 0 ? customWidth : 794;
        h = customHeight != null && customHeight > 0 ? customHeight : 1123;
        break;
      case "a4":
      default:
        w = 794;
        h = 1123;
        break;
    }
    if (!portrait) {
      int t = w;
      w = h;
      h = t;
    }
    return new HPage(w, h, m, m, m, m);
  }

  /** Apply paper size to every non-header/footer page of a presentation (export clone). */
  public static void applyToPresentationPages(
      org.hopper.presentation.HPresentation presentation, HPage paper) {
    if (presentation == null || paper == null || presentation.getPages() == null) {
      return;
    }
    for (HPage page : presentation.getPages()) {
      if (page == null || page.isHeader() || page.isFooter()) {
        continue;
      }
      page.setWidth(paper.getWidth());
      page.setHeight(paper.getHeight());
      page.setLeftMargin(paper.getLeftMargin());
      page.setRightMargin(paper.getRightMargin());
      page.setTopMargin(paper.getTopMargin());
      page.setBottomMargin(paper.getBottomMargin());
    }
  }
}
