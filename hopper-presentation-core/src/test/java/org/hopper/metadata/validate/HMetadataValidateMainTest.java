package org.hopper.metadata.validate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HMetadataValidateMainTest {

  @Test
  void testMainValidationWithCatalog() {
    Path file = Path.of("../hopper-presentation-project/demo/metadata/presentation/Maritime Executive Overview.json");
    Path catalog = Path.of("../hopper-presentation-project/demo/metadata");

    if (file.toFile().exists() && catalog.toFile().exists()) {
      assertDoesNotThrow(
          () ->
              HMetadataValidateMain.main(
                  new String[] {
                    "--type",
                    "presentation",
                    "--file",
                    file.toString(),
                    "--catalog",
                    catalog.toString(),
                    "--smoke"
                  }));
    }
  }
}
