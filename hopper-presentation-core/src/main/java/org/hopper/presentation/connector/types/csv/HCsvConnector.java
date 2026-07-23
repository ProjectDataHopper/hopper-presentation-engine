package org.hopper.presentation.connector.types.csv;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopPluginException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowDataUtil;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.IHRowListener;
import org.hopper.core.exception.HException;
import org.hopper.core.gui.form.HGuiFormConstants;
import org.hopper.core.gui.plugin.HWidgetElement;
import org.hopper.core.gui.plugin.HWidgetType;
import org.hopper.presentation.connector.type.HBaseConnector;
import org.hopper.presentation.connector.type.HConnectorPlugin;
import org.hopper.presentation.connector.type.IHConnector;
import org.hopper.presentation.datacontext.IDataContext;

/**
 * Source connector that streams rows from a CSV file opened via Hop VFS.
 *
 * <p>Configure column names/types manually or use the form action {@code detectCsvLayout} to sample
 * the header and the first 100 data rows.
 */
@JsonDeserialize(as = HCsvConnector.class)
@HConnectorPlugin(
    id = "CsvConnector",
    name = "CSV file",
    description = "Reads rows from a CSV file (Apache Commons VFS path/URI)",
    image = "ui/images/connectors/csv.svg")
@Getter
@Setter
public class HCsvConnector extends HBaseConnector implements IHConnector {

  public static final int DETECT_SAMPLE_ROWS = 100;

  /** Hop type names offered in the form editor (must match {@link ValueMetaFactory}). */
  public static final String[] FORM_TYPE_NAMES = {
    "String",
    "Integer",
    "Number",
    "BigNumber",
    "Boolean",
    "Date",
    "Timestamp",
    "Binary",
    "Internet Address"
  };

  public static final String[] ENCODING_OPTIONS = {
    "UTF-8", "ISO-8859-1", "Windows-1252", "UTF-16", "US-ASCII"
  };

  /** Common locale tags for number/date parsing (empty = JVM default). */
  public static final String[] LOCALE_OPTIONS = {
    "",
    "en_US",
    "en_GB",
    "de_DE",
    "fr_FR",
    "nl_NL",
    "es_ES",
    "it_IT",
    "pt_BR",
    "sv_SE",
    "pl_PL",
    "ru_RU",
    "ja_JP",
    "zh_CN"
  };

  private static final String[] DATE_MASKS = {
    "yyyy-MM-dd",
    "yyyy/MM/dd",
    "dd-MM-yyyy",
    "dd/MM/yyyy",
    "MM/dd/yyyy",
    "yyyy-MM-dd HH:mm:ss",
    "yyyy/MM/dd HH:mm:ss",
    "dd-MM-yyyy HH:mm:ss",
    "dd/MM/yyyy HH:mm:ss",
    "MM/dd/yyyy HH:mm:ss"
  };

  @HWidgetElement(
      order = "10000-filename",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.FILENAME,
      label = "Filename",
      toolTip = "Apache Commons VFS filename or URI (e.g. file:///data/sales.csv)")
  @HopMetadataProperty
  private String filename;

  @HWidgetElement(
      order = "10100-headerPresent",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.CHECKBOX,
      label = "Header present",
      toolTip = "When checked, the first non-empty line is treated as column names")
  @HopMetadataProperty
  private boolean headerPresent = true;

  @HWidgetElement(
      order = "10200-separator",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Separator",
      toolTip = "Field separator (use \\t for tab)")
  @HopMetadataProperty
  private String separator = ",";

  @HWidgetElement(
      order = "10250-locale",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Locale",
      toolTip =
          "Locale for decimal and grouping symbols when parsing numbers (and default date"
              + " parsing). Empty uses the JVM default locale.",
      comboValuesMethod = "getLocaleOptions")
  @HopMetadataProperty
  private String locale = "";

  @HWidgetElement(
      order = "10300-groupingSymbol",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Grouping symbol override",
      toolTip =
          "Optional override for thousands grouping (leave empty to use the Locale). Example: '.'"
              + " or ','")
  @HopMetadataProperty
  private String groupingSymbol = "";

  @HWidgetElement(
      order = "10400-encoding",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.COMBO,
      label = "Encoding",
      toolTip = "Character encoding of the CSV file",
      comboValuesMethod = "getEncodingOptions")
  @HopMetadataProperty
  private String encoding = "UTF-8";

  /**
   * Maximum number of data rows to read after the optional header. {@code 0} (default) means no
   * limit. Useful when testing large files.
   */
  @HWidgetElement(
      order = "10450-limit",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Limit",
      toolTip = "Max data rows to read (0 = unlimited). Does not include the header line.")
  @HopMetadataProperty
  private int limit = 0;

  /**
   * Form-only action (not metadata). Handled by hopper-presentation-rest {@code
   * hopperFormButtonClick} / detect-layout REST endpoint.
   */
  @JsonIgnore
  @HWidgetElement(
      order = "10500-detectCsvLayout",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.BUTTON,
      label = "Detect layout from file",
      toolTip =
          "Read the header (if present) and first 100 data rows to fill column names and types")
  private transient String detectCsvLayout;

  @HWidgetElement(
      order = "10600-fields",
      parentId = HGuiFormConstants.PARENT_PLUGIN,
      type = HWidgetType.TEXT,
      label = "Columns",
      toolTip = "Output column names, Hop types, format mask, length and precision")
  @HopMetadataProperty(key = "fields")
  private List<CsvField> fields;

  public HCsvConnector() {
    super("CsvConnector");
    this.fields = new ArrayList<>();
  }

  public HCsvConnector(HCsvConnector c) {
    super(c);
    this.filename = c.filename;
    this.headerPresent = c.headerPresent;
    this.separator = c.separator;
    this.locale = c.locale;
    this.groupingSymbol = c.groupingSymbol;
    this.encoding = c.encoding;
    this.limit = c.limit;
    this.fields = new ArrayList<>();
    if (c.fields != null) {
      c.fields.forEach(f -> this.fields.add(new CsvField(f)));
    }
  }

  @Override
  public HCsvConnector clone() {
    return new HCsvConnector(this);
  }

  /** Combo options for the encoding field. */
  public String[] getEncodingOptions() {
    return ENCODING_OPTIONS;
  }

  /** Combo options for the locale field (first entry is empty = JVM default). */
  public String[] getLocaleOptions() {
    return LOCALE_OPTIONS;
  }

  @Override
  public IRowMeta describeOutput(IDataContext dataContext) throws HException {
    try {
      NumberSymbols symbols = resolveNumberSymbols(locale, groupingSymbol);
      IRowMeta rowMeta = new RowMeta();
      if (fields != null) {
        for (CsvField field : fields) {
          rowMeta.addValueMeta(field.createValueMeta(symbols));
        }
      }
      return rowMeta;
    } catch (Exception e) {
      throw new HException("Error describing output of the CSV connector", e);
    }
  }

  @Override
  public void startStreaming(IDataContext dataContext) throws HException {
    IVariables variables = dataContext.getVariables();
    IRowMeta rowMeta = describeOutput(dataContext);
    if (rowMeta.isEmpty()) {
      throw new HException(
          "CSV connector has no columns configured. Use 'Detect layout from file' or add columns.");
    }

    String resolvedFile = Const.NVL(variables.resolve(filename), "").trim();
    if (StringUtils.isEmpty(resolvedFile)) {
      throw new HException("CSV filename is empty");
    }

    Charset charset = resolveCharset(encoding);
    String sep = resolveSeparator(separator);
    NumberSymbols symbols = resolveNumberSymbols(locale, groupingSymbol);
    Locale parseLocale = parseLocale(locale);

    int maxRows = Math.max(0, limit);

    try (InputStream in = HopVfs.getInputStream(resolvedFile, variables);
        Reader reader = new BufferedReader(new InputStreamReader(in, charset));
        CSVParser parser = buildFormat(sep, headerPresent).parse(reader)) {

      int rowsRead = 0;
      for (CSVRecord record : parser) {
        if (maxRows > 0 && rowsRead >= maxRows) {
          break;
        }
        Object[] rowData = RowDataUtil.allocateRowData(rowMeta.size());
        for (int i = 0; i < rowMeta.size(); i++) {
          IValueMeta valueMeta = rowMeta.getValueMeta(i);
          CsvField field = fields.get(i);
          String raw = i < record.size() ? record.get(i) : null;
          rowData[i] = convertCell(raw, valueMeta, field, symbols, parseLocale);
        }
        for (IHRowListener rowListener : rowListeners) {
          rowListener.rowReceived(rowMeta, rowData);
        }
        rowsRead++;
      }
      outputDone();
    } catch (HException e) {
      throw e;
    } catch (Exception e) {
      throw new HException("Error reading CSV file '" + resolvedFile + "'", e);
    }
  }

  @Override
  public void waitUntilFinished() throws HException {
    // Synchronous connector
  }

  /**
   * Detect column names and types from the configured file (header + up to {@link
   * #DETECT_SAMPLE_ROWS} data rows).
   */
  public List<CsvField> detectLayout(IVariables variables) throws HException {
    return HCsvLayoutDetector.detect(this, variables);
  }

  static CSVFormat buildFormat(String separator, boolean headerPresent) {
    CSVFormat.Builder builder =
        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(separator)
            .setQuote('"')
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .setIgnoreSurroundingSpaces(true);
    if (headerPresent) {
      builder.setHeader().setSkipHeaderRecord(true);
    }
    return builder.build();
  }

  static String resolveSeparator(String separator) {
    String s = Const.NVL(separator, ",");
    if ("\\t".equals(s) || "\t".equals(s)) {
      return "\t";
    }
    if (s.isEmpty()) {
      return ",";
    }
    return s;
  }

  static Charset resolveCharset(String encoding) {
    String enc = Const.NVL(encoding, "UTF-8").trim();
    try {
      return Charset.forName(enc);
    } catch (Exception e) {
      return StandardCharsets.UTF_8;
    }
  }

  /** Parse {@code language}, {@code language_COUNTRY}, or {@code language-COUNTRY}. */
  static Locale parseLocale(String localeTag) {
    String tag = Const.NVL(localeTag, "").trim();
    if (tag.isEmpty()) {
      return Locale.getDefault();
    }
    tag = tag.replace('-', '_');
    String[] parts = tag.split("_", 3);
    if (parts.length == 1) {
      return new Locale(parts[0]);
    }
    if (parts.length == 2) {
      return new Locale(parts[0], parts[1]);
    }
    return new Locale(parts[0], parts[1], parts[2]);
  }

  /**
   * Resolve decimal/grouping symbols from locale; optional {@code groupingOverride} replaces the
   * locale grouping character when non-empty.
   */
  static NumberSymbols resolveNumberSymbols(String localeTag, String groupingOverride) {
    Locale loc = parseLocale(localeTag);
    DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(loc);
    String decimal = String.valueOf(dfs.getDecimalSeparator());
    String grouping =
        StringUtils.isNotEmpty(groupingOverride)
            ? groupingOverride
            : String.valueOf(dfs.getGroupingSeparator());
    // DecimalFormatSymbols may use non-breaking space for grouping — normalize for string ops
    if (grouping != null && grouping.codePointAt(0) == 0x00A0) {
      grouping = " ";
    }
    return new NumberSymbols(decimal, grouping);
  }

  /** @deprecated use {@link #resolveNumberSymbols(String, String)} */
  @Deprecated
  static String decimalSymbolFor(String grouping) {
    if (".".equals(grouping)) {
      return ",";
    }
    return ".";
  }

  static Object convertCell(
      String raw, IValueMeta valueMeta, CsvField field, NumberSymbols symbols, Locale locale)
      throws HException {
    if (raw == null || raw.isEmpty()) {
      return null;
    }
    String text = raw.trim();
    if (text.isEmpty()) {
      return null;
    }
    try {
      return switch (valueMeta.getType()) {
        case IValueMeta.TYPE_STRING -> text;
        case IValueMeta.TYPE_INTEGER ->
            Long.parseLong(stripGrouping(text, symbols.grouping()));
        case IValueMeta.TYPE_NUMBER, IValueMeta.TYPE_BIGNUMBER ->
            Double.parseDouble(normalizeNumber(text, symbols));
        case IValueMeta.TYPE_BOOLEAN -> parseBoolean(text);
        case IValueMeta.TYPE_DATE, IValueMeta.TYPE_TIMESTAMP ->
            parseDate(text, field != null ? field.getFormatMask() : null, locale);
        default -> text;
      };
    } catch (Exception e) {
      throw new HException(
          "Cannot convert value '"
              + text
              + "' to "
              + valueMeta.getTypeDesc()
              + " for field '"
              + valueMeta.getName()
              + "'",
          e);
    }
  }

  /** Back-compat helper used by older tests / callers with only a grouping override. */
  static Object convertCell(String raw, IValueMeta valueMeta, String grouping) throws HException {
    NumberSymbols symbols = resolveNumberSymbols("", grouping);
    return convertCell(raw, valueMeta, null, symbols, Locale.getDefault());
  }

  static String stripGrouping(String text, String grouping) {
    if (StringUtils.isEmpty(grouping)) {
      return text;
    }
    return text.replace(grouping, "");
  }

  static String normalizeNumber(String text, NumberSymbols symbols) {
    String t = stripGrouping(text, symbols.grouping());
    String decimal = symbols.decimal();
    if (StringUtils.isNotEmpty(decimal) && !".".equals(decimal)) {
      // Remove any residual '.' thousands if decimal is comma, then map decimal to '.'
      if (",".equals(decimal)) {
        // already stripped grouping; replace decimal comma
        t = t.replace(',', '.');
      } else {
        t = t.replace(decimal, ".");
      }
    }
    return t;
  }

  static String normalizeNumber(String text, String grouping) {
    return normalizeNumber(text, resolveNumberSymbols("", grouping));
  }

  static Boolean parseBoolean(String text) {
    String t = text.trim().toLowerCase(Locale.ROOT);
    if ("true".equals(t) || "y".equals(t) || "yes".equals(t) || "1".equals(t)) {
      return Boolean.TRUE;
    }
    if ("false".equals(t) || "n".equals(t) || "no".equals(t) || "0".equals(t)) {
      return Boolean.FALSE;
    }
    throw new IllegalArgumentException("Not a boolean: " + text);
  }

  static Date parseDate(String text, String formatMask, Locale locale) {
    Locale loc = locale != null ? locale : Locale.getDefault();
    if (StringUtils.isNotEmpty(formatMask)) {
      try {
        SimpleDateFormat sdf = new SimpleDateFormat(formatMask, loc);
        sdf.setLenient(false);
        return sdf.parse(text);
      } catch (Exception e) {
        throw new IllegalArgumentException(
            "Not a date with mask '" + formatMask + "': " + text, e);
      }
    }
    for (String mask : DATE_MASKS) {
      try {
        SimpleDateFormat sdf = new SimpleDateFormat(mask, loc);
        sdf.setLenient(false);
        return sdf.parse(text);
      } catch (Exception ignored) {
        // try next
      }
    }
    throw new IllegalArgumentException("Not a date: " + text);
  }

  static Date parseDate(String text) {
    return parseDate(text, null, Locale.getDefault());
  }

  /** Decimal / grouping pair for number parsing. */
  public record NumberSymbols(String decimal, String grouping) {}

  @Getter
  @Setter
  public static final class CsvField {

    @HWidgetElement(
        order = "100-name",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Name")
    @HopMetadataProperty
    private String name;

    @HWidgetElement(
        order = "200-type",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.COMBO,
        label = "Type",
        comboValuesMethod = "getFormTypeNames")
    @HopMetadataProperty
    private String type;

    @HWidgetElement(
        order = "300-formatMask",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Format")
    @HopMetadataProperty
    private String formatMask;

    @HWidgetElement(
        order = "400-length",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Length")
    @HopMetadataProperty
    private String length;

    @HWidgetElement(
        order = "500-precision",
        parentId = HGuiFormConstants.PARENT_PLUGIN,
        type = HWidgetType.TEXT,
        label = "Precision")
    @HopMetadataProperty
    private String precision;

    public CsvField() {}

    public CsvField(CsvField f) {
      this.name = f.name;
      this.type = f.type;
      this.formatMask = f.formatMask;
      this.length = f.length;
      this.precision = f.precision;
    }

    public CsvField(String name, String type) {
      this.name = name;
      this.type = type;
    }

    public String[] getFormTypeNames() {
      return FORM_TYPE_NAMES;
    }

    public IValueMeta createValueMeta() throws HopPluginException {
      return createValueMeta(resolveNumberSymbols("", ""));
    }

    public IValueMeta createValueMeta(NumberSymbols symbols) throws HopPluginException {
      String typeName = Const.NVL(type, "String");
      int hopType = ValueMetaFactory.getIdForValueMeta(typeName);
      if (hopType < 0) {
        hopType = IValueMeta.TYPE_STRING;
      }
      IValueMeta valueMeta = ValueMetaFactory.createValueMeta(Const.NVL(name, "field"), hopType);
      valueMeta.setLength(Const.toInt(length, -1));
      valueMeta.setPrecision(Const.toInt(precision, -1));
      if (StringUtils.isNotEmpty(formatMask)) {
        valueMeta.setConversionMask(formatMask);
      }
      if (symbols != null) {
        if (StringUtils.isNotEmpty(symbols.decimal())) {
          valueMeta.setDecimalSymbol(symbols.decimal());
        }
        if (StringUtils.isNotEmpty(symbols.grouping())) {
          valueMeta.setGroupingSymbol(symbols.grouping());
        }
      }
      return valueMeta;
    }
  }
}
