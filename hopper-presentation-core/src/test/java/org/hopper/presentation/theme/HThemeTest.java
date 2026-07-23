package org.hopper.presentation.theme;

import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.util.TestUtil;

public class HThemeTest {

  public static final String THEME_NAME = "Theme1";
  private IHopMetadataProvider metadataProvider;

  public static final HTheme createTheme(String name) {
    HTheme theme = HTheme.getDefault();
    theme.setName(name);
    return theme;
  }

  @BeforeEach
  public void before() throws Exception {
    metadataProvider = new MemoryMetadataProvider();
  }

  @Test
  public void testThemeSaveLoad() throws Exception {

    IHopMetadataSerializer<HTheme> themeSerializer =
        metadataProvider.getSerializer(HTheme.class);

    HTheme theme = createTheme(THEME_NAME);
    themeSerializer.save(theme);

    // Load it back...
    //
    HTheme verify = themeSerializer.load(THEME_NAME);

    TestUtil.assertEqualThemes(theme, verify);
  }

  @AfterEach
  public void after() throws Exception {}
}
