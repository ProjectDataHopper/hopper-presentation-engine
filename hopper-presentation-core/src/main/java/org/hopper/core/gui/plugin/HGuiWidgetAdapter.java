package org.hopper.core.gui.plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.i18n.BaseMessages;

/**
 * Converts Hop {@link GuiWidgetElement} field annotations into Hopper {@link HWidgetElements}
 * descriptors so existing Hop plugins (e.g. variable resolvers) can drive Hopper browser forms via
 * {@link HGuiRegistry} without re-annotating Hop sources with {@link HWidgetElement}.
 */
public final class HGuiWidgetAdapter {

  /** packageName → merged properties from all classpath message files for that package. */
  private static final ConcurrentHashMap<String, Properties> PACKAGE_MESSAGES_CACHE =
      new ConcurrentHashMap<>();

  private HGuiWidgetAdapter() {}

  /**
   * Map a Hop GUI widget annotation on {@code field} into a Hopper widget descriptor, or {@code
   * null} when the widget type has no web-form equivalent (e.g. COMPOSITE).
   */
  public static HWidgetElements fromGuiWidgetElement(
      GuiWidgetElement annotation, Field field, Class<?> ownerClass) {
    if (annotation == null || field == null) {
      return null;
    }
    HWidgetType type = mapType(annotation.type());
    if (type == null || type == HWidgetType.NONE) {
      return null;
    }

    Class<?> resourceClass =
        field.getDeclaringClass() != null ? field.getDeclaringClass() : ownerClass;
    String i18nPackage = resourceClass != null ? resourceClass.getPackageName() : "";

    HWidgetElements child = new HWidgetElements();
    child.setField(field);
    child.setOwnerClass(ownerClass);
    child.setFieldName(field.getName());
    child.setFieldClass(field.getType());
    child.setId(StringUtils.isEmpty(annotation.id()) ? field.getName() : annotation.id());
    child.setOrder(annotation.order());
    child.setParentId(annotation.parentId());
    child.setType(type);
    child.setLabel(resolveI18n(annotation.label(), i18nPackage, resourceClass));
    child.setToolTip(resolveI18n(annotation.toolTip(), i18nPackage, resourceClass));
    child.setPassword(annotation.password());
    child.setVariablesEnabled(annotation.variables());
    child.setGetterMethod(annotation.getterMethod());
    child.setSetterMethod(annotation.setterMethod());
    child.setComboValuesMethod(annotation.comboValuesMethod());
    child.setComboSource(HComboSource.NONE);
    child.setIgnored(annotation.ignored());
    child.setSeparator(annotation.separator());
    // Hop 2.18 GuiWidgetElement has no multiLineTextHeight; default 1 (2.19+ can map later)
    child.setMultiLineTextHeight(1);
    return child;
  }

  static HWidgetType mapType(GuiElementType hopType) {
    if (hopType == null) {
      return HWidgetType.NONE;
    }
    // Hop 2.18.1 enum: NONE, TEXT, FILENAME, FOLDER, COMBO, CHECKBOX, METADATA, BUTTON, LINK,
    // COMPOSITE (MULTI_LINE_TEXT appears in later Hop; not present here.)
    return switch (hopType) {
      case TEXT -> HWidgetType.TEXT;
      case FILENAME -> HWidgetType.FILENAME;
      case FOLDER -> HWidgetType.FOLDER;
      case COMBO -> HWidgetType.COMBO;
      case CHECKBOX -> HWidgetType.CHECKBOX;
      case METADATA -> HWidgetType.METADATA;
      case BUTTON -> HWidgetType.BUTTON;
      case LINK -> HWidgetType.LINK;
      case COMPOSITE, NONE -> HWidgetType.NONE;
    };
  }

  /**
   * Resolve Hop GUI i18n strings.
   *
   * <p>Order:
   *
   * <ol>
   *   <li>{@link BaseMessages#getString(String, String, Class, String...)} (Hop desktop path)
   *   <li>Classpath multi-jar scan of {@code package/messages/messages_*.properties} — needed in a
   *       flat WAR classloader where hop-core and plugins share the same package path and the first
   *       ResourceBundle wins without plugin keys
   *   <li>Humanized leaf of the key (e.g. {@code ProjectId} → {@code Project Id})
   * </ol>
   */
  public static String resolveI18n(String text, String i18nPackage, Class<?> resourceClass) {
    if (StringUtils.isEmpty(text)) {
      return text;
    }
    try {
      String packageName = i18nPackage;
      String key = null;

      if (isUnresolved(text)) {
        key = text.substring(1, text.length() - 1);
      } else if (text.startsWith(Const.I18N_PREFIX)) {
        String[] parts = text.split(":", 3);
        if (parts.length == 3) {
          if (StringUtils.isNotEmpty(parts[1])) {
            packageName = parts[1];
          }
          key = parts[2];
        } else {
          return text;
        }
      } else if (looksLikeMessageKey(text)) {
        key = text;
      } else {
        // Already a human label
        return text;
      }

      if (StringUtils.isEmpty(key)) {
        return text;
      }

      // 1) Hop BaseMessages (works with separate plugin classloaders)
      String translated = tryBaseMessages(packageName, key, resourceClass);
      if (isUsable(translated)) {
        return translated;
      }

      // 2) Merge all jars that contribute messages for this package (WAR / single classloader)
      translated = tryClasspathMessages(packageName, key, resourceClass);
      if (isUsable(translated)) {
        return translated;
      }

      // 3) Readable fallback
      return humanizeKey(key);
    } catch (Exception e) {
      return text;
    }
  }

  /** Convenience for tests / callers that only have the owner class. */
  static String resolveI18n(String text, Class<?> ownerClass) {
    String pkg = ownerClass != null ? ownerClass.getPackageName() : "";
    return resolveI18n(text, pkg, ownerClass);
  }

  /**
   * Last resort: {@code GooleSecretManagerVariableResolver.label.ProjectId} → {@code Project Id}.
   */
  static String humanizeKey(String key) {
    if (StringUtils.isEmpty(key)) {
      return key;
    }
    int lastDot = key.lastIndexOf('.');
    String leaf = lastDot >= 0 ? key.substring(lastDot + 1) : key;
    String spaced = leaf.replaceAll("([a-z])([A-Z])", "$1 $2");
    return spaced.trim();
  }

  private static String tryBaseMessages(
      String packageName, String key, Class<?> resourceClass) {
    try {
      String translated;
      if (resourceClass != null && StringUtils.isNotEmpty(packageName)) {
        translated = BaseMessages.getString(packageName, key, resourceClass);
      } else if (resourceClass != null) {
        translated = BaseMessages.getString(resourceClass, key);
      } else if (StringUtils.isNotEmpty(packageName)) {
        translated = BaseMessages.getString(packageName, key);
      } else {
        return null;
      }
      return isUsable(translated) ? translated : null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Scan every {@code package/messages/messages_*.properties} on the classpath and return the first
   * value for {@code key}. Later jars (plugins) often carry keys missing from hop-core's shared
   * package bundle.
   */
  static String tryClasspathMessages(String packageName, String key, Class<?> resourceClass) {
    if (StringUtils.isEmpty(packageName) || StringUtils.isEmpty(key)) {
      return null;
    }
    Properties merged = PACKAGE_MESSAGES_CACHE.computeIfAbsent(packageName, HGuiWidgetAdapter::loadPackageMessages);
    String value = merged.getProperty(key);
    if (StringUtils.isNotEmpty(value)) {
      return value.trim();
    }
    // One-shot force reload via resource classloader if cache miss (plugin loaded late)
    if (resourceClass != null) {
      Properties extra = loadPackageMessages(packageName, resourceClass.getClassLoader());
      value = extra.getProperty(key);
      if (StringUtils.isNotEmpty(value)) {
        merged.putAll(extra);
        return value.trim();
      }
    }
    return null;
  }

  private static Properties loadPackageMessages(String packageName) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = HGuiWidgetAdapter.class.getClassLoader();
    }
    return loadPackageMessages(packageName, cl);
  }

  private static Properties loadPackageMessages(String packageName, ClassLoader classLoader) {
    Properties merged = new Properties();
    if (classLoader == null || StringUtils.isEmpty(packageName)) {
      return merged;
    }
    String basePath = packageName.replace('.', '/') + "/messages/";
    List<String> candidates = messageFileCandidates();
    for (String file : candidates) {
      String resourcePath = basePath + file;
      try {
        Enumeration<URL> urls = classLoader.getResources(resourcePath);
        while (urls.hasMoreElements()) {
          URL url = urls.nextElement();
          try (InputStream in = url.openStream();
              InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Properties p = new Properties();
            p.load(reader);
            // Later resources override earlier so plugin jars can win over hop-core
            merged.putAll(p);
          } catch (Exception ignored) {
            // skip unreadable resource
          }
        }
      } catch (Exception ignored) {
        // skip
      }
    }
    return merged;
  }

  private static List<String> messageFileCandidates() {
    List<String> files = new ArrayList<>();
    Locale locale = Locale.getDefault();
    String lang = locale.getLanguage();
    String country = locale.getCountry();
    // Hop default / failover first so en_US fills gaps, then more specific locales override
    files.add("messages_en_US.properties");
    files.add("messages.properties");
    if (StringUtils.isNotEmpty(lang) && StringUtils.isNotEmpty(country)) {
      files.add("messages_" + lang + "_" + country + ".properties");
    }
    if (StringUtils.isNotEmpty(lang)) {
      files.add("messages_" + lang + ".properties");
    }
    return files;
  }

  /** Keys look like {@code ClassName.label.Something} rather than free text. */
  private static boolean looksLikeMessageKey(String text) {
    if (text == null || text.contains(" ")) {
      return false;
    }
    return text.contains(".label.")
        || text.contains(".tooltip.")
        || text.contains(".Tooltip.")
        || text.contains(".Label.");
  }

  private static boolean isUsable(String translated) {
    return StringUtils.isNotEmpty(translated) && !isUnresolved(translated);
  }

  private static boolean isUnresolved(String translated) {
    return translated != null
        && translated.length() >= 2
        && translated.startsWith("!")
        && translated.endsWith("!");
  }
}
