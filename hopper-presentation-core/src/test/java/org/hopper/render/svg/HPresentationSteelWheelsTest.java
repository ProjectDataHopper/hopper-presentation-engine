package org.hopper.render.svg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.hopper.core.Constants;
import org.hopper.core.HJson;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.connector.HConnector;
import org.hopper.presentation.theme.HTheme;
import org.hopper.util.H2DatabaseUtil;

public class HPresentationSteelWheelsTest extends HPresentationTestBase {

  @Test
  public void testSteelWheelsJsonPresentationsRender() throws Exception {

    // populate steelwheels database and save connection in metastore
    //
    H2DatabaseUtil.createSteelWheelsDatabase(metadataProvider, variables);

    // Load connectors and themes used by the fixtures into the metadata catalog
    loadCatalogJson(
        new File("src/test/resources/metadata/connector/"), HConnector.class);
    loadCatalogJson(new File("src/test/resources/metadata/theme/"), HTheme.class);

    // Load all files in resources/presentations/*.json
    //
    File dir = new File("src/test/resources/presentations/");
    File[] files =
        dir.listFiles(
            new FilenameFilter() {
              @Override
              public boolean accept(File dir, String name) {
                return name.endsWith(".json");
              }
            });

    for (File file : files) {
      FileInputStream inputStream = new FileInputStream(file);
      try {
        String jsonString = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        HPresentation presentation = HPresentation.fromJsonString(jsonString);
        Path path = file.toPath();
        String baseFilename = path.getFileName().toString().replace(".json", "");

        if (StringUtils.isEmpty(presentation.getDefaultThemeName())) {
          presentation.setDefaultThemeName(Constants.DEFAULT_THEME_NAME);
        }
        if (metadataProvider.getSerializer(HTheme.class).load(presentation.getDefaultThemeName())
            == null) {
          HTheme theme = HTheme.getDefault();
          theme.setName(presentation.getDefaultThemeName());
          metadataProvider.getSerializer(HTheme.class).save(theme);
        }

        testRendering(presentation, baseFilename);

      } catch (Exception e) {
        IOUtils.closeQuietly(inputStream);
        throw e;
      }
    }
  }

  private <T extends org.apache.hop.metadata.api.IHopMetadata> void loadCatalogJson(
      File dir, Class<T> type) throws Exception {
    if (dir == null || !dir.isDirectory()) {
      return;
    }
    File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
    if (files == null) {
      return;
    }
    for (File file : files) {
      String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
      T object = HJson.createMapper().readValue(json, type);
      metadataProvider.getSerializer(type).save(object);
    }
  }
}
