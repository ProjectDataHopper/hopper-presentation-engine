package org.hopper.rest.resources.requests;

/**
 * Request body for {@code POST edit/connector/csv/detect-layout/}: sample a CSV file and return
 * column names/types without saving connector metadata.
 */
public class CsvDetectLayoutRequest {
  private String filename;
  private boolean headerPresent = true;
  private String separator = ",";
  private String locale = "";
  private String groupingSymbol = "";
  private String encoding = "UTF-8";

  public CsvDetectLayoutRequest() {}

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public boolean isHeaderPresent() {
    return headerPresent;
  }

  public void setHeaderPresent(boolean headerPresent) {
    this.headerPresent = headerPresent;
  }

  public String getSeparator() {
    return separator;
  }

  public void setSeparator(String separator) {
    this.separator = separator;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getGroupingSymbol() {
    return groupingSymbol;
  }

  public void setGroupingSymbol(String groupingSymbol) {
    this.groupingSymbol = groupingSymbol;
  }

  public String getEncoding() {
    return encoding;
  }

  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }
}
