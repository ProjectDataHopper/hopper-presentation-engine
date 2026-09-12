package org.hopper.presentation.swt;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * One-shot browser download on Hop Web (RAP) without a compile dependency on RAP. Desktop classpaths
 * have no {@code org.eclipse.rap.rwt.RWT}; {@link #start} then returns {@code false}.
 */
final class HRapDownload {

  private static final int TTL_MS = 120_000;

  private HRapDownload() {}

  static boolean start(Shell shell, String filename, String contentType, byte[] content) {
    if (shell == null || shell.isDisposed() || content == null) {
      return false;
    }
    Display display = shell.getDisplay();
    if (display == null || display.isDisposed()) {
      return false;
    }
    try {
      Class<?> rwt = Class.forName("org.eclipse.rap.rwt.RWT");
      Class<?> handlerType = Class.forName("org.eclipse.rap.rwt.service.ServiceHandler");
      Class<?> launcherType = Class.forName("org.eclipse.rap.rwt.client.service.UrlLauncher");
      Object serviceManager = rwt.getMethod("getServiceManager").invoke(null);
      Object client = rwt.getMethod("getClient").invoke(null);
      if (serviceManager == null || client == null) {
        return false;
      }
      Object launcher =
          client.getClass().getMethod("getService", Class.class).invoke(client, launcherType);
      if (launcher == null) {
        return false;
      }

      String serviceId = "hopper-presentation-export-" + UUID.randomUUID();
      AtomicBoolean served = new AtomicBoolean();
      Method unregister =
          serviceManager.getClass().getMethod("unregisterServiceHandler", String.class);
      Object handler =
          Proxy.newProxyInstance(
              handlerType.getClassLoader(),
              new Class<?>[] {handlerType},
              (proxy, method, args) -> {
                if ("service".equals(method.getName()) && args != null && args.length == 2) {
                  if (!served.compareAndSet(false, true)) {
                    sendNotFound(args[1]);
                    return null;
                  }
                  try {
                    writeAttachment(args[1], filename, contentType, content);
                  } catch (RuntimeException e) {
                    throw e;
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  } finally {
                    unregisterQuietly(unregister, serviceManager, serviceId);
                  }
                  return null;
                }
                return defaultObjectMethod(proxy, method, args, serviceId);
              });

      serviceManager
          .getClass()
          .getMethod("registerServiceHandler", String.class, handlerType)
          .invoke(serviceManager, serviceId, handler);
      String url =
          (String)
              serviceManager
                  .getClass()
                  .getMethod("getServiceHandlerUrl", String.class)
                  .invoke(serviceManager, serviceId);
      launcherType.getMethod("openURL", String.class).invoke(launcher, url);
      display.timerExec(
          TTL_MS,
          () -> {
            if (served.compareAndSet(false, true)) {
              unregisterQuietly(unregister, serviceManager, serviceId);
            }
          });
      return true;
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  private static void writeAttachment(
      Object response, String filename, String contentType, byte[] content) throws Exception {
    Class<?> responseClass = response.getClass();
    responseClass.getMethod("setContentType", String.class).invoke(response, contentType);
    try {
      responseClass
          .getMethod("setContentLengthLong", long.class)
          .invoke(response, (long) content.length);
    } catch (NoSuchMethodException e) {
      responseClass.getMethod("setContentLength", int.class).invoke(response, content.length);
    }
    responseClass
        .getMethod("setHeader", String.class, String.class)
        .invoke(response, "Cache-Control", "private, no-store");
    responseClass
        .getMethod("setHeader", String.class, String.class)
        .invoke(response, "Pragma", "no-cache");
    responseClass
        .getMethod("setHeader", String.class, String.class)
        .invoke(response, "X-Content-Type-Options", "nosniff");
    responseClass
        .getMethod("setHeader", String.class, String.class)
        .invoke(
            response,
            "Content-Disposition",
            HPresentationViewerSupport.contentDisposition(filename));
    Object stream = responseClass.getMethod("getOutputStream").invoke(response);
    stream.getClass().getMethod("write", byte[].class).invoke(stream, content);
    responseClass.getMethod("flushBuffer").invoke(response);
  }

  private static void sendNotFound(Object response) {
    try {
      response.getClass().getMethod("sendError", int.class).invoke(response, 404);
    } catch (Exception ignored) {
      // Best effort: the one-shot URL is already consumed or expired.
    }
  }

  private static void unregisterQuietly(
      Method unregister, Object serviceManager, String serviceId) {
    try {
      unregister.invoke(serviceManager, serviceId);
    } catch (Exception ignored) {
      // Already unregistered or the UI session ended.
    }
  }

  private static Object defaultObjectMethod(
      Object proxy, Method method, Object[] args, String serviceId) {
    return switch (method.getName()) {
      case "toString" -> "HRapDownload:" + serviceId;
      case "hashCode" -> serviceId.hashCode();
      case "equals" -> proxy == args[0];
      default -> null;
    };
  }
}