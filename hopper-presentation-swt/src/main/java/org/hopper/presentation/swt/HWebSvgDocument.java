package org.hopper.presentation.swt;

import java.util.Base64;

/**
 * HTML/JS shell that hosts a Hopper SVG in a RAP/SWT {@code Browser}. Java still layouts and
 * paints; this document only displays the SVG, maps pointer events, and replaces SVG in place so
 * live refresh does not reload the iframe. Fit modes size the SVG to the iframe inner box (with
 * inset) so RAP never shows a native scrollbar that would retrigger zoom.
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
    return html(svgXml, zoom, backgroundHex, 0, 0);
  }

  /**
   * Full HTML document. {@code pageW}/{@code pageH} are the unscaled presentation page size. Fit
   * modes ({@code mode}) recompute scale from {@code window.innerWidth/innerHeight}. Pass 0 to
   * measure from the SVG.
   */
  public static String html(String svgXml, float zoom, String backgroundHex, int pageW, int pageH) {
    return html(svgXml, zoom, backgroundHex, pageW, pageH, null);
  }

  public static String html(
      String svgXml,
      float zoom,
      String backgroundHex,
      int pageW,
      int pageH,
      HPresentationZoom mode) {
    String svg = svgXml == null ? "" : svgXml;
    String bg =
        backgroundHex == null || backgroundHex.isBlank() ? "#e6e6e6" : backgroundHex.trim();
    return "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
        + "html,body{margin:0;padding:0;background:"
        + bg
        + ";overflow:hidden;width:100%;height:100%;box-sizing:border-box;}"
        + "#slot{overflow:hidden;max-width:100%;max-height:100%;}"
        + "#page{transform-origin:top left;display:block;overflow:hidden;}"
        + "</style></head><body><div id='slot'><div id='page'>"
        + svg
        + "</div></div><script>"
        + script()
        + "setZoom("
        + zoomArgs(zoom, pageW, pageH, mode)
        + ");"
        + "</script></body></html>";
  }

  /** JavaScript to evaluate: {@code replaceSvg(<json>, <zoom>)}. */
  public static String replaceSvgScript(String svgXml, float zoom) {
    return replaceSvgScript(svgXml, zoom, 0, 0);
  }

  /** JavaScript to evaluate: {@code replaceSvg(<json>, <zoom>, <pageW>, <pageH>)}. */
  public static String replaceSvgScript(String svgXml, float zoom, int pageW, int pageH) {
    return replaceSvgScript(svgXml, zoom, pageW, pageH, null);
  }

  public static String replaceSvgScript(
      String svgXml, float zoom, int pageW, int pageH, HPresentationZoom mode) {
    return "replaceSvg("
        + jsString(svgXml == null ? "" : svgXml)
        + ","
        + zoomArgs(zoom, pageW, pageH, mode)
        + ");";
  }

  /**
   * JavaScript to evaluate: {@code downloadFile(name, mime, base64)}. Used when RAP's download
   * service is unavailable so Hop Web can still save SVG/PDF from the viewer Browser.
   */
  public static String downloadScript(String filename, String mimeType, byte[] content) {
    String b64 = content == null ? "" : Base64.getEncoder().encodeToString(content);
    return "downloadFile("
        + jsString(filename == null || filename.isBlank() ? "download" : filename)
        + ","
        + jsString(mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType)
        + ","
        + jsString(b64)
        + ");";
  }

  static String script() {
    return "var hopZoom=1;"
        + "function setZoom(z,pageW,pageH,mode){"
        + "var page=document.getElementById('page');"
        + "var slot=document.getElementById('slot');"
        + "if(!page||!slot){return;}"
        + "var svg=page.querySelector?page.querySelector('svg'):null;"
        + "var w=(typeof pageW==='number'&&pageW>0)?pageW:0;"
        + "var h=(typeof pageH==='number'&&pageH>0)?pageH:0;"
        + "if((w<=0||h<=0)&&svg){"
        + "var vb=(svg.viewBox&&svg.viewBox.baseVal)?svg.viewBox.baseVal:null;"
        + "if(w<=0){w=parseFloat(svg.getAttribute('width'))||(vb?vb.width:0)||page.scrollWidth||1;}"
        + "if(h<=0){h=parseFloat(svg.getAttribute('height'))||(vb?vb.height:0)||page.scrollHeight||1;}"
        + "}"
        + "if(w<=0){w=1;}if(h<=0){h=1;}"
        + "var vw=window.innerWidth||document.documentElement.clientWidth||0;"
        + "var vh=window.innerHeight||document.documentElement.clientHeight||0;"
        + "var margin=24;"
        + "var availW=Math.max(1,vw-margin);"
        + "var availH=Math.max(1,vh-margin);"
        + "var widthZ=availW/w;"
        + "var heightZ=availH/h;"
        + "var m=(typeof mode==='string'?mode:'').toUpperCase();"
        + "if(m==='WIDTH'){hopZoom=widthZ;}"
        + "else if(m==='HEIGHT'){hopZoom=heightZ;}"
        + "else if(m==='PAGE'){hopZoom=Math.min(widthZ,heightZ);}"
        + "else if(m==='ACTUAL'){hopZoom=1;}"
        + "else{hopZoom=(typeof z==='number'&&isFinite(z)&&z>0)?z:1;}"
        + "if(m==='WIDTH'||m==='HEIGHT'||m==='PAGE'){"
        + "hopZoom=Math.min(hopZoom,widthZ,heightZ);"
        + "}"
        + "if(!(hopZoom>0&&isFinite(hopZoom))){hopZoom=1;}"
        + "var sw=Math.max(1,Math.round(w*hopZoom));"
        + "var sh=Math.max(1,Math.round(h*hopZoom));"
        + "if(m==='WIDTH'||m==='HEIGHT'||m==='PAGE'){"
        + "if(sw>availW){hopZoom=availW/w;sw=Math.max(1,Math.round(w*hopZoom));sh=Math.max(1,Math.round(h*hopZoom));}"
        + "if(sh>availH){hopZoom=availH/h;sw=Math.max(1,Math.round(w*hopZoom));sh=Math.max(1,Math.round(h*hopZoom));}"
        + "}"
        + "page.style.transform='none';"
        + "page.style.width=sw+'px';"
        + "page.style.height=sh+'px';"
        + "if(svg){"
        + "if(!svg.getAttribute('viewBox')){svg.setAttribute('viewBox','0 0 '+w+' '+h);}"
        + "svg.setAttribute('width',String(sw));"
        + "svg.setAttribute('height',String(sh));"
        + "svg.style.width=sw+'px';"
        + "svg.style.height=sh+'px';"
        + "svg.style.maxWidth='100%';"
        + "svg.style.maxHeight='100%';"
        + "}"
        + "slot.style.width=sw+'px';"
        + "slot.style.height=sh+'px';"
        + "var fit=m==='WIDTH'||m==='HEIGHT'||m==='PAGE';"
        + "var ov=fit?'hidden':((sw>vw+2||sh>vh+2)?'auto':'hidden');"
        + "document.documentElement.style.overflow=ov;"
        + "document.body.style.overflow=ov;"
        + "}"
        + "function replaceSvg(svg,z,pageW,pageH,mode){"
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
        + "if(typeof z==='number'){setZoom(z,pageW,pageH,mode);}"
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
        + "document.addEventListener('dblclick',function(e){pt(e,'DOUBLE_CLICK');});"
        + "function downloadFile(name,mime,b64){"
        + "try{"
        + "var bin=atob(b64||'');"
        + "var bytes=new Uint8Array(bin.length);"
        + "for(var i=0;i<bin.length;i++){bytes[i]=bin.charCodeAt(i);}"
        + "var blob=new Blob([bytes],{type:mime||'application/octet-stream'});"
        + "var url=URL.createObjectURL(blob);"
        + "var a=document.createElement('a');"
        + "a.href=url;"
        + "a.download=name||'download';"
        + "a.rel='noopener';"
        + "document.body.appendChild(a);"
        + "a.click();"
        + "document.body.removeChild(a);"
        + "setTimeout(function(){URL.revokeObjectURL(url);},2000);"
        + "}catch(e){}"
        + "}";
  }

  private static String zoomArgs(float zoom, int pageW, int pageH, HPresentationZoom mode) {
    String args = zoomLiteral(zoom) + "," + Math.max(0, pageW) + "," + Math.max(0, pageH);
    if (mode != null) {
      args += ",\"" + mode.name() + "\"";
    }
    return args;
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
