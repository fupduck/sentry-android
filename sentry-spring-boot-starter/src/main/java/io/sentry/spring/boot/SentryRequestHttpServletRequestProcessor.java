package io.sentry.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.sentry.core.EventProcessor;
import io.sentry.core.SentryEvent;
import io.sentry.core.protocol.Request;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Attaches information about HTTP request to {@link SentryEvent}. */
@Open
public class SentryRequestHttpServletRequestProcessor implements EventProcessor {

  @Override
  public @NotNull SentryEvent process(final @NotNull SentryEvent event, final @Nullable Object hint) {
    final RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      final HttpServletRequest request =
          ((ServletRequestAttributes) requestAttributes).getRequest();
      event.setRequest(resolveSentryRequest(request));
    }
    return event;
  }

  @SuppressWarnings("JdkObsolete")
  private static @NotNull Request resolveSentryRequest(
      final @NotNull HttpServletRequest httpRequest) {
    final Request sentryRequest = new Request();
    sentryRequest.setMethod(httpRequest.getMethod());
    sentryRequest.setQueryString(httpRequest.getQueryString());
    sentryRequest.setUrl(httpRequest.getRequestURL().toString());
    sentryRequest.setHeaders(resolveHeadersMap(httpRequest));
    sentryRequest.setCookies(toString(httpRequest.getHeaders("Cookie")));
    return sentryRequest;
  }

  private static @NotNull Map<String, String> resolveHeadersMap(
      final @NotNull HttpServletRequest request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      headersMap.put(headerName, toString(request.getHeaders(headerName)));
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable Enumeration<String> enumeration) {
    return enumeration != null ? String.join(",", Collections.list(enumeration)) : null;
  }
}
