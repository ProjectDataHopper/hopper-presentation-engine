package org.hopper.presentation.swt;

/**
 * HTML/JS shell that hosts a Hopper SVG in a RAP/SWT {@code Browser}. Java still layouts and
 * paints; this document only displays the SVG, maps pointer events, and replaces SVG in place so
 * live refresh does not reload the iframe.
 */
public final class HWebSvgDocument {

  private HWebSvgDocument() {}

  /**
   * Full HTML document for the first {@code Browser.setText}. Later updates should use {@link
   * #replaceSvgScript(String, float)} instead of rebuilding the document.
   */
  public static String html(String svgXml, float zoom) {
    return html(svgXml, zoom, "#e6e6e6");
  }

  public static String html(String svgXml, float zoom, String backgroundHex) {
    String svg = svgXml == null ? "" : svgXml;
    String bg =
        backgroundHex == null || backgroundHex.isBlank() ? "#e6e6e6" : backgroundHex.trim();
    return "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
        + "html,body{margin:0;padding:0;background:"
        + bg
        + ";overflow:auto;}"
        + "#page{transform-origin:top left;display:inline-block;}"
        + "</style></head><body><div id='page'>"
        + svg
        + "</div><script>"
        + script()
        + "setZoom("
        + zoomLiteral(zoom)
        + ");"
        + "</script></body></html>";
  }

  /** JavaScript to evaluate: {@code replaceSvg(<json>, <zoom>)}. */
  public static String replaceSvgScript(String svgXml, float zoom) {
    return "replaceSvg(" + jsString(svgXml == null ? "" : svgXml) + "," + zoomLiteral(zoom) + ");";
  }

  static String script() {
    return "var hopZoom=1;"
        + "function setZoom(z){"
        + "hopZoom=(typeof z==='number'&&isFinite(z)&&z>0)?z:1;"
        + "var p=document.getElementById('page');"
        + "if(p){p.style.transformOrigin='top left';p.style.transform='scale('+hopZoom+')';}"
        + "}"
        + "function replaceSvg(svg,z){"
        + "var page=document.getElementById('page');"
        + "if(!page){return;}"
        + "try{"
        + "var doc=new DOMParser().parseFromString(svg||'','image/svg+xml');"
        + "var root=doc.documentElement;"
        + "if(root&&root.nodeName&&root.nodeName.toLowerCase()==='svg'"
        + "&&(!root.querySelector||!root.querySelector('parsererror'))){"
        + "while(page.firstChild){page.removeChild(page.firstChild);}"
        + "page.appendChild(document.importNode(root,true));"
        + "}else{page.innerHTML=svg||'';}"
        + "}catch(e){page.innerHTML=svg||'';}"
        + "if(typeof z==='number'){setZoom(z);}"
        + "}"
        + "function pt(e,m){"
        + "var page=document.getElementById('page');"
        + "if(!page){return;}"
        + "var r=page.getBoundingClientRect();"
        + "if(window.hopPointer){"
        + "hopPointer((e.clientX-r.left)/hopZoom,(e.clientY-r.top)/hopZoom,m);"
        + "}"
        + "}"
        + "document.addEventListener('click',function(e){pt(e,'SINGLE_CLICK');});"
        + "document.addEventListener('dblclick',function(e){pt(e,'DOUBLE_CLICK');});";
  }

  static String jsString(String value) {
    String s = value == null ? "" : value;
    StringBuilder out = new StringBuilder(s.length() + 16);
    out.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '"' -> out.append("\\\"");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '<' -> {
          if (i + 1 < s.length() && s.charAt(i + 1) == '/') {
            out.append("\\x3c");
          } else {
            out.append(c);
          }
        }
        default -> {
          if (c < 32) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
    return out.toString();
  }

  private static String zoomLiteral(float zoom) {
    if (!Float.isFinite(zoom) || zoom <= 0f) {
      return "1";
    }
    return Float.toString(zoom);
  }
}
