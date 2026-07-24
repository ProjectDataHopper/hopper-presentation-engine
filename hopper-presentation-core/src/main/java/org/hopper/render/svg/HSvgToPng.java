package org.hopper.render.svg;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.commons.lang3.StringUtils;
import org.hopper.core.exception.HException;

/**
 * Rasterize Batik/SVG markup to PNG for fast browser display.
 *
 * <p>HTMLImageElement decode of some dark-themed SVGs is multi-second in Chromium; PNG decode is
 * typically a few ms in both light and dark UI modes.
 *
 * <p>Default rasterization uses a pixel scale &gt; 1 so soft-reload PNGs stay sharp on HiDPI
 * canvases ({@code devicePixelRatio} 2+). Presentation coordinates stay in SVG user units; the
 * client divides image natural size by the scale when computing draw scale and hit tests.
 *
 * <p>Note: {@code KEY_PIXEL_UNIT_TO_MILLIMETER} does <em>not</em> change output pixel dimensions for
 * SVGs that already specify width/height in px — use {@code KEY_WIDTH}/{@code KEY_HEIGHT} instead.
 */
public final class HSvgToPng {

  /**
   * Soft-reload / editor PNG density relative to SVG user units. 2× matches common
   * {@code devicePixelRatio} and still downscales cleanly on 1× displays.
   */
  public static final float DEFAULT_PIXEL_SCALE = 2f;

  /** Root SVG width/height in user units (px), e.g. {@code width="1123"}. */
  private static final Pattern WIDTH_ATTR =
      Pattern.compile("\\bwidth\\s*=\\s*[\"']\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:px)?\\s*[\"']",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern HEIGHT_ATTR =
      Pattern.compile("\\bheight\\s*=\\s*[\"']\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:px)?\\s*[\"']",
          Pattern.CASE_INSENSITIVE);
  /** viewBox="minX minY width height" fallback when width/height attrs missing. */
  private static final Pattern VIEW_BOX =
      Pattern.compile(
          "\\bviewBox\\s*=\\s*[\"']\\s*[0-9.+-eE]+\\s+[0-9.+-eE]+\\s+([0-9.eE+-]+)\\s+([0-9.eE+-]+)\\s*[\"']",
          Pattern.CASE_INSENSITIVE);

  private HSvgToPng() {}

  /**
   * @param svgXml complete SVG document
   * @return PNG bytes at {@link #DEFAULT_PIXEL_SCALE}
   */
  public static byte[] toPngBytes(String svgXml) throws HException {
    return toPngBytes(svgXml, DEFAULT_PIXEL_SCALE);
  }

  /**
   * @param svgXml complete SVG document
   * @param pixelScale output pixels per SVG user unit (≥ 1 recommended; &lt; 0.25 clamped to 1)
   * @return PNG bytes
   */
  public static byte[] toPngBytes(String svgXml, float pixelScale) throws HException {
    if (StringUtils.isBlank(svgXml)) {
      throw new HException("Cannot convert empty SVG to PNG");
    }
    float scale = pixelScale < 0.25f ? 1f : pixelScale;
    try {
      PNGTranscoder transcoder = new PNGTranscoder();
      float[] size = parseSvgUserSize(svgXml);
      if (size != null && size[0] > 0 && size[1] > 0) {
        // Force output pixel size — the only reliable way to multi-sample in Batik
        transcoder.addTranscodingHint(
            SVGAbstractTranscoder.KEY_WIDTH, size[0] * scale);
        transcoder.addTranscodingHint(
            SVGAbstractTranscoder.KEY_HEIGHT, size[1] * scale);
      }
      TranscoderInput input = new TranscoderInput(new StringReader(svgXml));
      ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(8192, svgXml.length()));
      TranscoderOutput output = new TranscoderOutput(baos);
      transcoder.transcode(input, output);
      return baos.toByteArray();
    } catch (Exception e) {
      throw new HException("Error converting SVG to PNG", e);
    }
  }

  /**
   * Parse root SVG width/height in user units for KEY_WIDTH/HEIGHT scaling.
   *
   * @return {@code float[]{width, height}} or null if unknown
   */
  static float[] parseSvgUserSize(String svgXml) {
    if (svgXml == null) {
      return null;
    }
    // Prefer attributes on the opening <svg ...> tag only (first 2k is enough)
    int svgOpen = svgXml.indexOf("<svg");
    if (svgOpen < 0) {
      svgOpen = svgXml.indexOf("<SVG");
    }
    String head =
        svgOpen >= 0
            ? svgXml.substring(svgOpen, Math.min(svgXml.length(), svgOpen + 2500))
            : svgXml.substring(0, Math.min(svgXml.length(), 2500));
    Float w = firstFloat(WIDTH_ATTR, head);
    Float h = firstFloat(HEIGHT_ATTR, head);
    if (w != null && h != null && w > 0 && h > 0) {
      return new float[] {w, h};
    }
    Matcher vb = VIEW_BOX.matcher(head);
    if (vb.find()) {
      try {
        float vw = Float.parseFloat(vb.group(1));
        float vh = Float.parseFloat(vb.group(2));
        if (vw > 0 && vh > 0) {
          return new float[] {vw, vh};
        }
      } catch (NumberFormatException ignored) {
        // fall through
      }
    }
    return null;
  }

  private static Float firstFloat(Pattern pattern, String text) {
    Matcher m = pattern.matcher(text);
    if (!m.find()) {
      return null;
    }
    try {
      return Float.parseFloat(m.group(1));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Base64 (no data: prefix) for JSON transport. */
  public static String toPngBase64(String svgXml) throws HException {
    return toPngBase64(svgXml, DEFAULT_PIXEL_SCALE);
  }

  public static String toPngBase64(String svgXml, float pixelScale) throws HException {
    return Base64.getEncoder().encodeToString(toPngBytes(svgXml, pixelScale));
  }
}
