package org.hopper.presentation.layout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.svg.HopSvgGraphics2D;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.hopper.core.HGeometry;
import org.hopper.core.draw.DrawnItem;
import org.hopper.core.exception.HException;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.datacontext.IDataContext;
import org.hopper.presentation.page.HPage;
import lombok.Getter;
import lombok.Setter;

/**
 * Contains layout results of a presentation.
 *
 * <p>Multi-part components (e.g. tables that paginate) call {@link #addComponentGeometry} once per
 * part. Relative layout of later siblings must use the <b>first</b> part's geometry so charts
 * attached to a table's right edge resolve against page&nbsp;1, not the short last-page fragment.
 * {@link #findGeometry} continues to return the <b>last</b> geometry (group envelopes grow per row).
 */
@Getter
@Setter
public class HLayoutResults {

  /** Last geometry written for a component name (existing callers / growing envelopes). */
  private Map<String, HGeometry> componentGeometryMap;

  /** First geometry written for a component name (relative attachment resolution). */
  private Map<String, HGeometry> firstComponentGeometryMap;

  private Map<String, Map<String, Object>> componentDataSetMap;

  private List<HRenderPage> renderPages;

  private ILogChannel log;

  private IDataContext dataContext;

  private String id;

  public HLayoutResults(ILogChannel log) {
    this.log = log;
    componentGeometryMap = new HashMap<>();
    firstComponentGeometryMap = new HashMap<>();
    componentDataSetMap = new HashMap<>();
    renderPages = new ArrayList<>();
    id = UUID.randomUUID().toString();
  }

  /**
   * Last geometry registered for the component (group components update this as rows grow).
   */
  public HGeometry findGeometry(String componentName) {
    return componentGeometryMap.get(componentName);
  }

  /**
   * First geometry registered for the component. Prefer this when resolving relative layout
   * attachments so multi-page tables expose a stable page-1 box to siblings.
   */
  public HGeometry findFirstGeometry(String componentName) {
    HGeometry first = firstComponentGeometryMap.get(componentName);
    if (first != null) {
      return first;
    }
    return componentGeometryMap.get(componentName);
  }

  public void addComponentGeometry(String componentName, HGeometry geometry) {
    if (componentName == null || geometry == null) {
      return;
    }
    if (!firstComponentGeometryMap.containsKey(componentName)) {
      // Clone so later mutations of part geometry do not alter the first-part snapshot
      firstComponentGeometryMap.put(
          componentName, new HGeometry(geometry.getX(), geometry.getY(), geometry.getWidth(), geometry.getHeight()));
    }
    componentGeometryMap.put(componentName, geometry);
  }

  public void addDataSet(HComponent component, String key, Object dataSet) {
    Map<String, Object> dataSetMap = componentDataSetMap.get(component.getName());
    if (dataSetMap == null) {
      dataSetMap = new HashMap<>();
      componentDataSetMap.put(component.getName(), dataSetMap);
    }
    dataSetMap.put(key, dataSet);
  }

  public Object getDataSet(HComponent component, String key) {
    Map<String, Object> dataSetMap = componentDataSetMap.get(component.getName());
    if (dataSetMap == null) {
      return null;
    }
    return dataSetMap.get(key);
  }

  public HRenderPage addNewPage(HPage page, HRenderPage currentRenderPage) {
    int pageNumber;
    if (currentRenderPage == null) {
      pageNumber = 1;
    } else {
      pageNumber = currentRenderPage.getPageNumber() + 1;
    }
    // If we're dealing with a header or a footer we obviously don't want to add extra render pages
    //
    if (page.isHeader() || page.isFooter()) {
      if (currentRenderPage == null) {
        HRenderPage renderPage = new HRenderPage(page);
        renderPage.setPageNumber(pageNumber);
        renderPages.add(renderPage);
        return renderPage;
      } else {
        return currentRenderPage;
      }
    } else {
      // Regular rendering page: always create a new one...
      //
      HRenderPage renderPage = new HRenderPage(page);
      renderPage.setPageNumber(pageNumber);
      renderPages.add(renderPage);
      return renderPage;
    }
  }

  /**
   * Most recently created render page for this logical page (used while multi-page tables
   * continue flowing).
   */
  public HRenderPage getCurrentRenderPage(HPage page) {
    for (int i = renderPages.size() - 1; i >= 0; i--) {
      HPage renderPage = renderPages.get(i).getPage();

      // The header & footer flag or the page number give us what we need.
      //
      if (page.isHeader() && renderPage.isHeader()
          || page.isFooter() && renderPage.isFooter()
          || renderPage.equals(page)) {
        return renderPages.get(i);
      }
    }

    // No page with this number found, create a new one...
    //
    return addNewPage(page, null);
  }

  /**
   * First render page for this logical page. Non-flowing components (charts, labels, …) that are
   * laid out after a multi-page table should place themselves here so they appear next to the
   * table on page&nbsp;1 instead of only on the last overflow page.
   */
  public HRenderPage getFirstRenderPage(HPage page) {
    for (int i = 0; i < renderPages.size(); i++) {
      HPage renderPage = renderPages.get(i).getPage();
      if (page.isHeader() && renderPage.isHeader()
          || page.isFooter() && renderPage.isFooter()
          || renderPage.equals(page)) {
        return renderPages.get(i);
      }
    }
    return addNewPage(page, null);
  }

  public void replaceGCForHeaderFooter(HopSvgGraphics2D gc) throws HException {

    if (renderPages.size() > 1) {
      throw new HException("Multi-page headers or footers are not supported!");
    }
    if (renderPages.isEmpty()) {
      return;
    }

    HRenderPage renderPage = renderPages.get(0);
    renderPage.setGc(gc);
  }

  public void replaceDrawnItemsForHeaderFooter(List<DrawnItem> drawnItems) throws HException {
    if (renderPages.size() > 1) {
      throw new HException("Multi-page headers or footers are not supported!");
    }
    if (renderPages.isEmpty()) {
      return;
    }

    HRenderPage renderPage = renderPages.get(0);
    renderPage.setDrawnItems(drawnItems);
  }

  /** Set the page numbers on the render pages */
  public void setRenderPageNumbers() {
    for (int i = 0; i < renderPages.size(); i++) {
      renderPages.get(i).setPageNumber(i + 1);
    }
  }

  public void saveSvgPages(
      String baseFolder,
      String baseName,
      boolean convertToPdfs,
      boolean mergePdfs,
      boolean splitFolders)
      throws HException {

    if (splitFolders) {
      File baseParent = new File(baseFolder);
      if (!baseParent.exists()) {
        baseParent.mkdirs();
      }
      File svgsFolder = new File(baseParent.toString() + File.separator + "svgs");
      if (!svgsFolder.exists()) {
        svgsFolder.mkdirs();
      }
      File pdfsFolder = new File(baseParent.toString() + File.separator + "pdfs");
      if (!pdfsFolder.exists()) {
        pdfsFolder.mkdirs();
      }
      File pdfFolder = new File(baseParent.toString() + File.separator + "pdf");
      if (!pdfFolder.exists()) {
        pdfFolder.mkdirs();
      }
    }

    PDFMergerUtility mergerUtility = null;
    if (convertToPdfs && mergePdfs) {
      // For performance reasons
      System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
      mergerUtility = new PDFMergerUtility();
    }
    List<String> svgFilenames = new ArrayList<>();
    List<String> pdfFilenames = new ArrayList<>();

    for (int i = 0; i < renderPages.size(); i++) {
      HRenderPage renderPage = renderPages.get(i);

      String svgFilename;
      String pdfFilename;
      if (splitFolders) {
        svgFilename =
            baseFolder
                + File.separator
                + "svgs"
                + File.separator
                + baseName
                + "_"
                + (i + 1)
                + ".svg";
        pdfFilename =
            baseFolder
                + File.separator
                + "pdfs"
                + File.separator
                + baseName
                + "_"
                + (i + 1)
                + ".pdf";
      } else {
        svgFilename = baseFolder + File.separator + baseName + "_" + (i + 1) + ".svg";
        pdfFilename = baseFolder + File.separator + baseName + "_" + (i + 1) + ".pdf";
      }

      svgFilenames.add(svgFilename);
      pdfFilenames.add(pdfFilename);

      try (FileOutputStream stream = new FileOutputStream(svgFilename)) {
        try {
          stream.write(renderPage.getSvgXml().getBytes("UTF-8"));
          stream.flush();
        } catch (Exception e) {
          throw new HException("Unable to convert rendering to SVG XML", e);
        }

        // Save file to PDF as well...
        //
        if (convertToPdfs) {
          try (FileInputStream svgInputStream = new FileInputStream(svgFilename)) {
            TranscoderInput input = new TranscoderInput(svgInputStream);
            TranscoderOutput output = new TranscoderOutput(new FileOutputStream(pdfFilename));
            PDFTranscoder transcoder = new PDFTranscoder();
            transcoder.transcode(input, output);
          } catch (Exception e) {
            throw new HException(
                "Unable to transcode SVG '" + svgFilename + "' to PDF '" + pdfFilename, e);
          }

          if (mergePdfs) {
            mergerUtility.addSource(pdfFilename);
          }
        }

      } catch (IOException e) {
        throw new HException("Unable to write to file " + svgFilename, e);
      }
    }

    if (convertToPdfs && mergePdfs) {
      String pdfFilename;
      if (splitFolders) {
        pdfFilename = baseFolder + File.separator + "pdf" + File.separator + baseName + ".pdf";
      } else {
        pdfFilename = baseFolder + File.separator + baseName + ".pdf";
      }
      mergerUtility.setDestinationFileName(pdfFilename);
      try {
        mergerUtility.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
      } catch (IOException e) {
        throw new HException("Error merging PDF documents into " + pdfFilename, e);
      }
    }
  }
}
