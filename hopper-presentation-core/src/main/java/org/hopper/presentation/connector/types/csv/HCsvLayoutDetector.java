package org.hopper.presentation.connector.types.csv;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.hopper.core.exception.HException;
import org.hopper.presentation.connector.types.csv.HCsvConnector.CsvField;

/** Samples a CSV file to infer column names and Hop data types. */
public final class HCsvLayoutDetector {

  private HCsvLayoutDetector() {}

  public static List<CsvField> detect(HCsvConnector connector, IVariables variables)
      throws HException {
    if (connector == null) {
      throw new HException("CSV connector is required for layout detection");
    }
    String resolvedFile =
        Const.NVL(
                variables != null
                    ? variables.resolve(connector.getFilename())
                    : connector.getFilename(),
                "")
            .trim();
    if (StringUtils.isEmpty(resolvedFile)) {
      throw new HException("CSV filename is empty");
    }

    boolean headerPresent = connector.isHeaderPresent();
    String sep = HCsvConnector.resolveSeparator(connector.getSeparator());
    HCsvConnector.NumberSymbols symbols =
        HCsvConnector.resolveNumberSymbols(connector.getLocale(), connector.getGroupingSymbol());
    Charset charset = HCsvConnector.resolveCharset(connector.getEncoding());

    // For detection we always parse raw records (no auto header skip) so we control naming.
    CSVFormat format =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(sep)
            .setQuote('"')
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setIgnoreSurroundingSpaces(true)
            .get();

    try (InputStream in =
            variables != null
                ? HopVfs.getInputStream(resolvedFile, variables)
                : HopVfs.getInputStream(resolvedFile);
        Reader reader = new BufferedReader(new InputStreamReader(in, charset));
        CSVParser parser = format.parse(reader)) {

      List<String> names = null;
      List<List<String>> samples = new ArrayList<>();
      int dataRows = 0;

      for (CSVRecord record : parser) {
        if (names == null) {
          if (headerPresent) {
            names = headerNames(record);
            continue;
          }
          names = syntheticNames(record.size());
        }

        List<String> row = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
          row.add(i < record.size() ? Const.NVL(record.get(i), "").trim() : "");
        }
        samples.add(row);
        dataRows++;
        if (dataRows >= HCsvConnector.DETECT_SAMPLE_ROWS) {
          break;
        }
      }

      if (names == null || names.isEmpty()) {
        throw new HException("CSV file '" + resolvedFile + "' has no columns to detect");
      }

      List<CsvField> fields = new ArrayList<>(names.size());
      for (int col = 0; col < names.size(); col++) {
        String type = inferType(samples, col, symbols);
        fields.add(new CsvField(names.get(col), type));
      }
      return fields;
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Error detecting CSV layout from '" + resolvedFile + "'", e);
    }
  }

  private static List<String> headerNames(CSVRecord record) {
    List<String> names = new ArrayList<>(record.size());
    for (int i = 0; i < record.size(); i++) {
      String n = Const.NVL(record.get(i), "").trim();
      if (n.isEmpty()) {
        n = "field_" + (i + 1);
      }
      names.add(uniqueName(names, n));
    }
    return names;
  }

  private static List<String> syntheticNames(int width) {
    List<String> names = new ArrayList<>(width);
    for (int i = 0; i < width; i++) {
      names.add("field" + (i + 1));
    }
    return names;
  }

  private static String uniqueName(List<String> existing, String base) {
    String name = base;
    int n = 2;
    while (existing.contains(name)) {
      name = base + "_" + n;
      n++;
    }
    return name;
  }

  /**
   * Infer a single Hop type for column {@code col} from sample values. Empty cells are ignored.
   * Mixed types fall back to String.
   */
  static String inferType(List<List<String>> samples, int col, String grouping) {
    return inferType(samples, col, HCsvConnector.resolveNumberSymbols("", grouping));
  }

  static String inferType(
      List<List<String>> samples, int col, HCsvConnector.NumberSymbols symbols) {
    String candidate = null;
    for (List<String> row : samples) {
      if (col >= row.size()) {
        continue;
      }
      String v = row.get(col);
      if (StringUtils.isEmpty(v)) {
        continue;
      }
      String t = classifyValue(v, symbols);
      if (candidate == null) {
        candidate = t;
      } else if (!candidate.equals(t)) {
        // Widen Integer → Number when mixed numeric
        if (("Integer".equals(candidate) && "Number".equals(t))
            || ("Number".equals(candidate) && "Integer".equals(t))) {
          candidate = "Number";
        } else {
          return "String";
        }
      }
    }
    return candidate != null ? candidate : "String";
  }

  static String classifyValue(String text, String grouping) {
    return classifyValue(text, HCsvConnector.resolveNumberSymbols("", grouping));
  }

  static String classifyValue(String text, HCsvConnector.NumberSymbols symbols) {
    String t = text.trim();
    if (isBoolean(t)) {
      return "Boolean";
    }
    if (isInteger(t, symbols)) {
      return "Integer";
    }
    if (isNumber(t, symbols)) {
      return "Number";
    }
    if (isDate(t)) {
      return "Date";
    }
    return "String";
  }

  private static boolean isBoolean(String t) {
    String lower = t.toLowerCase(Locale.ROOT);
    return "true".equals(lower)
        || "false".equals(lower)
        || "y".equals(lower)
        || "n".equals(lower)
        || "yes".equals(lower)
        || "no".equals(lower);
  }

  private static boolean isInteger(String t, HCsvConnector.NumberSymbols symbols) {
    try {
      Long.parseLong(HCsvConnector.stripGrouping(t, symbols.grouping()));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isNumber(String t, HCsvConnector.NumberSymbols symbols) {
    try {
      Double.parseDouble(HCsvConnector.normalizeNumber(t, symbols));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isDate(String t) {
    try {
      HCsvConnector.parseDate(t);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
