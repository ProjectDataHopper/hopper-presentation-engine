package org.hopper.presentation.swt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.hop.core.logging.LoggingObjectType;
import org.apache.hop.core.logging.SimpleLoggingObject;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.api.IHopMetadataSerializer;
import org.apache.hop.metadata.serializer.memory.MemoryMetadataProvider;
import org.apache.hop.ui.hopgui.HopGuiEnvironment;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.hopper.core.HAttachment;
import org.hopper.core.HEnvironment;
import org.hopper.core.HHorizontalAlignment;
import org.hopper.core.HVerticalAlignment;
import org.hopper.presentation.HPresentation;
import org.hopper.presentation.component.HComponent;
import org.hopper.presentation.component.types.label.HLabelComponent;
import org.hopper.presentation.interaction.HInteraction;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.interaction.HInteractionLocation;
import org.hopper.presentation.interaction.HInteractionMethod;
import org.hopper.presentation.layout.HLayout;
import org.hopper.presentation.page.HPage;
import org.hopper.presentation.theme.HTheme;

/** Standalone demo of {@link HPresentationViewer} with in-memory sample presentations. */
public class HPresentationTestViewer {

  public static void main(String[] args) {
    try {
      HopGuiEnvironment.init();
      HEnvironment.init();

      Display display = new Display();
      SimpleLoggingObject loggingObject =
          new SimpleLoggingObject("HPV", LoggingObjectType.GENERAL, null);

      IHopMetadataProvider metadataProvider = new MemoryMetadataProvider();
      HTheme theme = HTheme.getDefault();
      metadataProvider.getSerializer(HTheme.class).save(theme);

      String dashboardName = "Dashboard";
      List<String> presentationNames =
          Arrays.asList(
              "Presentation A",
              "Presentation B",
              "Presentation C",
              "Presentation D",
              "Presentation E");
      for (String presentationName : presentationNames) {
        generateLabelPresentation(metadataProvider, dashboardName, presentationName);
      }
      generateDashboard(metadataProvider, dashboardName, presentationNames);

      Shell shell = new Shell(display, SWT.APPLICATION_MODAL | SWT.CLOSE | SWT.RESIZE);
      shell.setLayout(new FormLayout());
      shell.setText("Hopper SWT Viewer");

      HPresentationViewer wViewer =
          new HPresentationViewer(shell, loggingObject, metadataProvider, dashboardName);
      FormData fdViewer = new FormData();
      fdViewer.left = new FormAttachment(0, 0);
      fdViewer.top = new FormAttachment(0, 0);
      fdViewer.right = new FormAttachment(100, 0);
      fdViewer.bottom = new FormAttachment(100, 0);
      wViewer.setLayoutData(fdViewer);

      shell.layout();
      shell.setSize(900, 1200);
      shell.open();
      shell.addListener(SWT.Close, e -> display.dispose());

      while (!display.isDisposed()) {
        if (!display.readAndDispatch()) {
          display.sleep();
        }
      }
      System.exit(0);
    } catch (Exception e) {
      System.err.println("Error encountered: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static HInteractionLocation location(String componentName) {
    return new HInteractionLocation(
        componentName, "HLabelComponent", null, null, Collections.emptyList());
  }

  private static void generateDashboard(
      IHopMetadataProvider metadataProvider, String dashboardName, List<String> presentationNames)
      throws Exception {
    IHopMetadataSerializer<HPresentation> serializer =
        metadataProvider.getSerializer(HPresentation.class);

    HPresentation presentation = new HPresentation();
    presentation.setName(dashboardName);

    HPage page = HPage.getA4(true);
    presentation.getPages().add(page);

    String relativeTopComponent = null;
    for (String presentationName : presentationNames) {
      HLabelComponent label = new HLabelComponent();
      label.setLabel(presentationName);
      label.setHorizontalAlignment(HHorizontalAlignment.LEFT);
      label.setVerticalAlignment(HVerticalAlignment.BOTTOM);

      HComponent labelComponent = new HComponent(presentationName, label);
      HLayout labelLayout = new HLayout();
      labelLayout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.CENTER));
      if (relativeTopComponent == null) {
        labelLayout.setTop(new HAttachment(null, 0, 100, HAttachment.Alignment.TOP));
      } else {
        labelLayout.setTop(
            new HAttachment(relativeTopComponent, 0, 30, HAttachment.Alignment.BOTTOM));
      }
      labelComponent.setLayout(labelLayout);

      presentation
          .getInteractions()
          .add(
              new HInteraction(
                  HInteractionMethod.SINGLE_CLICK,
                  location(presentationName),
                  new HInteractionAction(
                      HInteractionAction.ActionType.OPEN_PRESENTATION, presentationName)));

      page.getComponents().add(labelComponent);
      relativeTopComponent = presentationName;
    }

    HTheme theme = HTheme.getDefault();
    presentation.setDefaultThemeName(theme.getName());
    serializer.save(presentation);
  }

  private static void generateLabelPresentation(
      IHopMetadataProvider metadataProvider, String dashboardName, String presentationName)
      throws Exception {
    IHopMetadataSerializer<HPresentation> serializer =
        metadataProvider.getSerializer(HPresentation.class);

    HPresentation presentation = new HPresentation();
    presentation.setName(presentationName);

    HPage page = HPage.getA4(true);
    presentation.getPages().add(page);

    HLabelComponent title = new HLabelComponent();
    title.setLabel(presentationName);
    HComponent labelComponent = new HComponent("Label", title);
    HLayout labelLayout = new HLayout();
    labelLayout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.CENTER));
    labelLayout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.CENTER));
    labelComponent.setLayout(labelLayout);
    page.getComponents().add(labelComponent);

    HLabelComponent back = new HLabelComponent();
    back.setLabel("Back to : " + dashboardName);
    back.setHorizontalAlignment(HHorizontalAlignment.CENTER);
    back.setVerticalAlignment(HVerticalAlignment.MIDDLE);
    HComponent dashComponent = new HComponent(dashboardName, back);
    HLayout dashLayout = new HLayout();
    dashLayout.setRight(new HAttachment(null, 0, 0, HAttachment.Alignment.RIGHT));
    dashLayout.setBottom(new HAttachment(null, 0, 0));
    dashComponent.setLayout(dashLayout);
    page.getComponents().add(dashComponent);

    presentation
        .getInteractions()
        .add(
            new HInteraction(
                HInteractionMethod.SINGLE_CLICK,
                location(dashboardName),
                new HInteractionAction(
                    HInteractionAction.ActionType.OPEN_PRESENTATION, dashboardName)));

    HTheme theme = HTheme.getDefault();
    presentation.setDefaultThemeName(theme.getName());
    serializer.save(presentation);
  }
}
