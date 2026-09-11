package org.hopper.presentation.host;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.hopper.core.draw.DrawnItem;
import org.hopper.presentation.interaction.HInteractionAction;
import org.hopper.presentation.variable.HParameter;

/**
 * Side-effect a host (SWT, REST/JS, CLI) should perform after a pointer interaction. The session
 * never opens windows or paints tooltips itself.
 */
@Getter
@Builder
public class HHostCommand {

  public enum Type {
    OPEN_PRESENTATION,
    OPEN_LINK,
    OPEN_LINK_NEW_TAB,
    POPUP_CONTEXT_INFORMATION,
    POPUP_PRESENTATION
  }

  private final Type type;
  private final String objectName;
  private final List<HParameter> parameters;
  private final DrawnItem drawnItem;
  private final String title;
  private final String text;

  public static HHostCommand fromAction(HInteractionAction action, DrawnItem drawnItem) {
    if (action == null || action.getActionType() == null) {
      return null;
    }
    var ctx = drawnItem != null ? drawnItem.getContext() : null;
    String name = action.resolveObjectName(ctx);
    List<HParameter> params = action.collectParameters(ctx);
    return switch (action.getActionType()) {
      case OPEN_PRESENTATION -> HHostCommand.builder()
          .type(Type.OPEN_PRESENTATION)
          .objectName(name)
          .parameters(params)
          .drawnItem(drawnItem)
          .build();
      case OPEN_LINK_SAME_TAB -> HHostCommand.builder()
          .type(Type.OPEN_LINK)
          .objectName(name)
          .drawnItem(drawnItem)
          .build();
      case OPEN_LINK_NEW_TAB -> HHostCommand.builder()
          .type(Type.OPEN_LINK_NEW_TAB)
          .objectName(name)
          .drawnItem(drawnItem)
          .build();
      case POPUP_CONTEXT_INFORMATION -> HHostCommand.builder()
          .type(Type.POPUP_CONTEXT_INFORMATION)
          .objectName(name)
          .title(name)
          .text(contextTooltipText(ctx))
          .drawnItem(drawnItem)
          .build();
      case POPUP_PRESENTATION -> HHostCommand.builder()
          .type(Type.POPUP_PRESENTATION)
          .objectName(name)
          .parameters(params)
          .drawnItem(drawnItem)
          .build();
    };
  }

  static String contextTooltipText(org.hopper.core.draw.DrawnContext ctx) {
    if (ctx == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if (ctx.getValue() != null && !ctx.getValue().isBlank()) {
      sb.append(ctx.getValue());
    }
    if (ctx.getDimensionValues() != null && !ctx.getDimensionValues().isEmpty()) {
      ctx.getDimensionValues()
          .forEach(
              (k, v) -> {
                if (sb.length() > 0) {
                  sb.append('\n');
                }
                sb.append(k).append(": ").append(v);
              });
    }
    return sb.toString();
  }
}
