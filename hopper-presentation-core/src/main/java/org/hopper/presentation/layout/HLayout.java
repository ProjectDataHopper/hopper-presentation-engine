package org.hopper.presentation.layout;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.hopper.core.HAttachment;
import org.hopper.core.exception.HException;
import org.hopper.presentation.component.HComponent;

/**
 * In case a position is not relative it means absolute vs the top and left margins of the page. In
 * that situation, you simply set or get the (x,y) position and you're done.
 *
 * <p>In case the position is relative versus another component, you need to provide a bunch of
 * details for the x and y coordinates.
 */
@Getter
@Setter
@NoArgsConstructor
public class HLayout {

  @HopMetadataProperty private HAttachment left;
  @HopMetadataProperty private HAttachment right;
  @HopMetadataProperty private HAttachment top;
  @HopMetadataProperty private HAttachment bottom;

  public HLayout(int x, int y) {
    left = new HAttachment(0, x);
    top = new HAttachment(0, y);
  }

  public HLayout(
      HAttachment left, HAttachment right, HAttachment top, HAttachment bottom) {
    this.left = left;
    this.right = right;
    this.top = top;
    this.bottom = bottom;
  }

  public HLayout(HLayout layout) {
    this.left = layout.left == null ? null : new HAttachment(layout.left);
    this.right = layout.right == null ? null : new HAttachment(layout.right);
    this.top = layout.top == null ? null : new HAttachment(layout.top);
    this.bottom = layout.bottom == null ? null : new HAttachment(layout.bottom);
  }

  public static HLayout topLeftPage() {
    HLayout layout = new HLayout();
    layout.left = new HAttachment(null, 0, 0, HAttachment.Alignment.LEFT);
    layout.top = new HAttachment(null, 0, 0, HAttachment.Alignment.TOP);
    return layout;
  }

  public static HLayout fullPage() {
    HLayout layout = topLeftPage();
    layout.right = new HAttachment(null, 0, 0, HAttachment.Alignment.RIGHT);
    layout.bottom = new HAttachment(null, 0, 0, HAttachment.Alignment.BOTTOM);
    return layout;
  }

  public static HLayout under(String otherComponent, boolean spanPageWidth) {
    HLayout layout = new HLayout();
    layout.left = new HAttachment(otherComponent, 0, 0, HAttachment.Alignment.LEFT);
    layout.top = new HAttachment(otherComponent, 0, 0, HAttachment.Alignment.BOTTOM);
    if (spanPageWidth) {
      layout.right = new HAttachment(null, 0, 0, HAttachment.Alignment.RIGHT);
    }
    return layout;
  }

  public static HLayout right(String otherComponent, boolean spanPageWidth) {
    HLayout layout = new HLayout();
    layout.left = new HAttachment(otherComponent, 0, 0, HAttachment.Alignment.RIGHT);
    layout.top = new HAttachment(otherComponent, 0, 0, HAttachment.Alignment.TOP);
    if (spanPageWidth) {
      layout.right = new HAttachment(null, 0, 0, HAttachment.Alignment.RIGHT);
    }
    return layout;
  }

  public void replaceReferences(String oldName, String newName) {
    for (HAttachment attachment : new HAttachment[] {left, top, right, bottom}) {
      if (attachment != null && oldName.equals(attachment.getComponentName())) {
        attachment.setComponentName(newName);
      }
    }
  }

  public Set<String> getReferencedLayoutComponentNames() {
    Set<String> names = new HashSet<>();
    for (HAttachment attachment : new HAttachment[] {left, top, right, bottom}) {
      if (attachment != null && StringUtils.isNotEmpty(attachment.getComponentName())) {
        names.add(attachment.getComponentName());
      }
    }
    return names;
  }

  public boolean hasLeft() {
    return left != null;
  }

  public boolean hasTop() {
    return top != null;
  }

  public boolean hasRight() {
    return right != null;
  }

  public boolean hasBottom() {
    return bottom != null;
  }

  public int numberOfAnchors() {
    int anchors = 0;
    if (hasLeft()) {
      anchors++;
    }
    if (hasRight()) {
      anchors++;
    }
    if (hasTop()) {
      anchors++;
    }
    if (hasBottom()) {
      anchors++;
    }
    return anchors;
  }

  public void validate(HComponent component) throws HException {
    if (hasLeft()) {
      switch (left.getAlignment()) {
        case TOP:
        case BOTTOM:
          throw new HException(
              "Setting a TOP or BOTTOM alignment makes no sense for left attachments on component "
                  + component.getName());
        default:
          break;
      }
    }
    if (hasTop()) {
      switch (top.getAlignment()) {
        case LEFT:
        case RIGHT:
          throw new HException(
              "Setting a LEFT or RIGHT alignment makes no sense for top attachments on component "
                  + component.getName());
        default:
          break;
      }
    }
    if (hasRight()) {
      switch (right.getAlignment()) {
        case TOP:
        case BOTTOM:
          throw new HException(
              "Setting a TOP or BOTTOM alignment makes no sense for right attachments on component "
                  + component.getName());
        default:
          break;
      }
    }
    if (hasBottom()) {
      switch (bottom.getAlignment()) {
        case LEFT:
        case RIGHT:
          throw new HException(
              "Setting a LEFT or RIGHT alignment makes no sense for bottom attachments on component "
                  + component.getName());
        default:
          break;
      }
    }
  }
}
