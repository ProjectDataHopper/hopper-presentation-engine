package org.hopper.presentation.layout;

import org.hopper.core.HAttachment;

public class HLayoutBuilder {
  private HLayout layout;

  public HLayoutBuilder() {
    layout = new HLayout(null, null, null, null);
  }

  public HLayout build() {
    return layout;
  }

  public HLayoutBuilder all() {
    return left().top().right().bottom();
  }

  public HLayoutBuilder all(int margin) {
    return left(margin).top(margin).right(-margin).bottom(-margin);
  }

  /**
   * Specify a position at the top of the parent
   *
   * @return the builder
   */
  public HLayoutBuilder top() {
    layout.setTop(new HAttachment(null, 0, 0, HAttachment.Alignment.TOP));
    return this;
  }

  /**
   * Specify a position at the top of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder top(int offset) {
    return top(0, offset);
  }

  /**
   * Specify a position at the top of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder top(int percentage, int offset) {
    layout.setTop(new HAttachment(null, percentage, offset, HAttachment.Alignment.TOP));
    return this;
  }

  /**
   * Specify a position at the left of the parent
   *
   * @return the builder
   */
  public HLayoutBuilder left() {
    layout.setLeft(new HAttachment(null, 0, 0, HAttachment.Alignment.LEFT));
    return this;
  }

  /**
   * Specify a position at the left of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder left(int offset) {
    return left(0, offset);
  }

  /**
   * Specify a left boundary in percentage to the left of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder left(int percentage, int offset) {
    layout.setLeft(new HAttachment(null, percentage, offset, HAttachment.Alignment.LEFT));
    return this;
  }

  /**
   * Specify a boundary at the right of the parent
   *
   * @return the builder
   */
  public HLayoutBuilder right() {
    layout.setRight(new HAttachment(null, 0, 0, HAttachment.Alignment.RIGHT));
    return this;
  }

  /**
   * Specify a boundary at the right of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder right(int offset) {
    return right(0, offset);
  }

  /**
   * Specify a right boundary in percentage to the right of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder right(int percentage, int offset) {
    layout.setRight(new HAttachment(null, percentage, offset, HAttachment.Alignment.RIGHT));
    return this;
  }

  /**
   * Specify a boundary at the bottom of the parent
   *
   * @return the builder
   */
  public HLayoutBuilder bottom() {
    layout.setBottom(new HAttachment(null, 0, 0, HAttachment.Alignment.BOTTOM));
    return this;
  }

  /**
   * Specify a boundary at the bottom of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder bottom(int offset) {
    return bottom(0, offset);
  }

  /**
   * Specify a bottom boundary in percentage to the bottom of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder bottom(int percentage, int offset) {
    layout.setBottom(new HAttachment(null, percentage, offset, HAttachment.Alignment.BOTTOM));
    return this;
  }

  /**
   * Specify a bottom boundary relative to the top of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder bottomFromTop(int percentage, int offset) {
    layout.setBottom(new HAttachment(null, percentage, offset, HAttachment.Alignment.TOP));
    return this;
  }

  /**
   * Specify a top boundary relative to the bottom of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder topFromBottom(int percentage, int offset) {
    layout.setTop(new HAttachment(null, percentage, offset, HAttachment.Alignment.BOTTOM));
    return this;
  }

  /**
   * Specify a position at the top of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder topFromBottom(String referenceComponent, int percentage, int offset) {
    layout.setTop(new HAttachment(referenceComponent, percentage, offset, HAttachment.Alignment.BOTTOM));
    return this;
  }

  /**
   * Specify a top using the top of the referenced component
   *
   * @return the builder
   */
  public HLayoutBuilder topFromTop(String referenceComponent, int percentage, int offset) {
    layout.setTop(new HAttachment(referenceComponent, percentage, offset, HAttachment.Alignment.TOP));
    return this;
  }

  /**
   * Specify a bottom using the top of the referenced component
   *
   * @return the builder
   */
  public HLayoutBuilder bottomFromTop(String referenceComponent, int percentage, int offset) {
    layout.setBottom(new HAttachment(referenceComponent, percentage, offset, HAttachment.Alignment.TOP));
    return this;
  }


  /**
   * Specify a position right below another component with a vertical offset
   *
   * @return the builder
   */
  public HLayoutBuilder below(String referenceComponent, int verticalOffset) {
    layout.setLeft(new HAttachment(referenceComponent, 0, 0, HAttachment.Alignment.LEFT));
    layout.setTop(new HAttachment(referenceComponent, 0, verticalOffset, HAttachment.Alignment.BOTTOM));
    return this;
  }

  /**
   * Specify a left boundary relative to the right of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder leftFromRight(int percentage, int offset) {
    layout.setLeft(new HAttachment(null, percentage, offset, HAttachment.Alignment.RIGHT));
    return this;
  }

  /**
   * Specify a left boundary relative to the center of the parent
   *
   * @return the builder
   */
  public HLayoutBuilder leftCenter() {
    return leftCenter(0,0);
  }

  /**
   * Specify a left boundary relative to the center of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder leftCenter( int percentage, int offset) {
    layout.setLeft(new HAttachment(null, percentage, offset, HAttachment.Alignment.CENTER));
    return this;
  }

  /**
   * Specify a top boundary relative to the center of the parent
   *
   * @return the builder
   */
  public HLayoutBuilder topFromCenter() {
    return topCenter(0,0);
  }

  /**
   * Specify a top boundary relative to the center of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder topCenter(int percentage, int offset) {
    layout.setTop(new HAttachment(null, percentage, offset, HAttachment.Alignment.CENTER));
    return this;
  }

  /**
   * Specify a right boundary relative to the left of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder rightFromLeft(int percentage, int offset) {
    layout.setRight(new HAttachment(null, percentage, offset, HAttachment.Alignment.LEFT));
    return this;
  }

  /**
   * Specify a right boundary relative to the left of the parent with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder rightFromLeft(String referenceComponent, int percentage, int offset) {
    layout.setRight(new HAttachment(referenceComponent, percentage, offset, HAttachment.Alignment.LEFT));
    return this;
  }


  /**
   * Specify a left boundary relative to the right of the reference component with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder leftFromRight(String componentName, int percentage, int offset) {
    layout.setLeft(new HAttachment(componentName, percentage, offset, HAttachment.Alignment.RIGHT));
    return this;
  }

  /**
   * Specify a right boundary relative to the right of the reference component with an offset
   *
   * @return the builder
   */
  public HLayoutBuilder rightFromRight(String componentName, int percentage, int offset) {
    layout.setRight(new HAttachment(componentName, percentage, offset, HAttachment.Alignment.RIGHT));
    return this;
  }

  /**
   * Specify a right boundary relative to the center of the reference component
   *
   * @return the builder
   */
  public HLayoutBuilder rightFromCenter(String componentName, int percentage, int offset) {
    layout.setRight(new HAttachment(componentName, percentage, offset, HAttachment.Alignment.CENTER));
    return this;
  }


  /**
   * Specify a position to the right of another component with a horizontal offset
   *
   * @return the builder
   */
  public HLayoutBuilder beside(String referenceComponent, int horizontalOffset) {
    layout.setLeft(new HAttachment(referenceComponent, 0, horizontalOffset, HAttachment.Alignment.RIGHT));
    layout.setTop(new HAttachment(referenceComponent, 0, 0, HAttachment.Alignment.TOP));
    return this;
  }

  public HLayoutBuilder left( HAttachment attachment ) {
    layout.setLeft( attachment );
    return this;
  }

  public HLayoutBuilder top( HAttachment attachment ) {
    layout.setTop( attachment );
    return this;
  }

  public HLayoutBuilder right( HAttachment attachment ) {
    layout.setRight( attachment );
    return this;
  }

  public HLayoutBuilder bottom( HAttachment attachment ) {
    layout.setBottom( attachment );
    return this;
  }
}
