package org.hopper.presentation;

import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hopper.core.HEnvironment;
import org.hopper.util.BasePresentationUtil;
import org.hopper.util.TestUtil;

public class HPresentationJsonTest {

  private IHopMetadataProvider metadataProvider;
  private IVariables variables;

  @BeforeEach
  public void setUp() throws Exception {
    metadataProvider = new MemoryMetadataProvider();
    variables = Variables.getADefaultVariableSpace();
    HEnvironment.init();
  }

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void testJson() throws Exception {

    HPresentation[] presentations =
        new BasePresentationUtil(metadataProvider, variables).getAvailablePresentations();

    for (HPresentation presentation : presentations) {
      String jsonString = presentation.toJsonString();
      HPresentation verify = HPresentation.fromJsonString(jsonString);
      TestUtil.assertEqualPresentations(presentation, verify);
    }
  }

  @Test
  public void testMetaStore() throws Exception {

    IHopMetadataSerializer<HPresentation> presentationSerializer =
        metadataProvider.getSerializer(HPresentation.class);

    HPresentation[] presentations =
        new BasePresentationUtil(metadataProvider, variables).getAvailablePresentations();

    for (HPresentation presentation : presentations) {

      presentationSerializer.save(presentation);
      HPresentation verify = presentationSerializer.load(presentation.getName());

      TestUtil.assertEqualPresentations(presentation, verify);
    }
  }
}
