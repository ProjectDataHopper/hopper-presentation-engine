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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.hopper.core.exception.HException;
import org.hopper.presentation.layout.HLayoutResults;
import org.hopper.presentation.layout.HRenderPage;

/**
 * Convert one or more presentation SVG pages into a multi-page PDF (Batik/FOP transcoder + PDFBox
 * merge).
 */
public final class HSvgPdfExporter {

  private HSvgPdfExporter() {}

  /**
   * Build a single multi-page PDF from layout results (one PDF page per render page).
   *
   * @param layoutResults layout with rendered SVG pages
   * @return PDF bytes
   */
  public static byte[] fromLayoutResults(HLayoutResults layoutResults) throws HException {
    if (layoutResults == null || layoutResults.getRenderPages() == null) {
      throw new HException("No layout results to export as PDF");
    }
    List<String> svgs = new ArrayList<>();
    for (HRenderPage page : layoutResults.getRenderPages()) {
      if (page == null) {
        continue;
      }
      String svg = page.getSvgXml();
      if (svg != null && !svg.isBlank()) {
        svgs.add(svg);
      }
    }
    return mergeSvgsToPdf(svgs);
  }

  /**
   * Transcode each SVG document to a one-page PDF and merge into one multi-page PDF.
   *
   * @param svgXmlPages ordered SVG documents (UTF-8)
   * @return merged PDF
   */
  public static byte[] mergeSvgsToPdf(List<String> svgXmlPages) throws HException {
    if (svgXmlPages == null || svgXmlPages.isEmpty()) {
      throw new HException("No SVG pages to export as PDF");
    }
    // Align with HLayoutResults.saveSvgPages: prefer KMS for FOP/PDF performance
    System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");

    List<byte[]> pagePdfs = new ArrayList<>();
    int index = 0;
    for (String svg : svgXmlPages) {
      index++;
      if (svg == null || svg.isBlank()) {
        continue;
      }
      try {
        pagePdfs.add(svgToPdfBytes(svg));
      } catch (Exception e) {
        throw new HException("Unable to transcode SVG page " + index + " to PDF", e);
      }
    }
    if (pagePdfs.isEmpty()) {
      throw new HException("No non-empty SVG pages to export as PDF");
    }
    if (pagePdfs.size() == 1) {
      return pagePdfs.get(0);
    }
    try {
      return mergePdfParts(pagePdfs);
    } catch (Exception e) {
      throw new HException("Error merging PDF pages", e);
    }
  }

  private static byte[] svgToPdfBytes(String svgXml) throws Exception {
    byte[] svgBytes = svgXml.getBytes(StandardCharsets.UTF_8);
    try (InputStream in = new ByteArrayInputStream(svgBytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      TranscoderInput input = new TranscoderInput(in);
      TranscoderOutput output = new TranscoderOutput(out);
      PDFTranscoder transcoder = new PDFTranscoder();
      transcoder.transcode(input, output);
      return out.toByteArray();
    }
  }

  private static byte[] mergePdfParts(List<byte[]> pagePdfs) throws Exception {
    PDFMergerUtility merger = new PDFMergerUtility();
    ByteArrayOutputStream merged = new ByteArrayOutputStream();
    merger.setDestinationStream(merged);
    for (byte[] part : pagePdfs) {
      merger.addSource(new ByteArrayInputStream(part));
    }
    merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
    return merged.toByteArray();
  }
}
