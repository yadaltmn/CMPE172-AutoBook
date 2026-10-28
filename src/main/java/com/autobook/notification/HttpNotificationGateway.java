package com.autobook.notification;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link NotificationGateway} that calls the notification service's REST API with a bounded
 * connect and read timeout, and translates HTTP failures into {@link NotificationDeliveryException}.
 */
@Component
public class HttpNotificationGateway implements NotificationGateway {

    static final String NOTIFICATIONS_PATH = "/mock-notifications/api/notifications";

    private static final Logger log = LoggerFactory.getLogger(HttpNotificationGateway.class);

    private final RestClient restClient;
    private final NotificationProperties properties;
    private final Environment environment;

    public HttpNotificationGateway(NotificationProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public NotificationReceipt send(NotificationRequest request) {
        String url = baseUrl() + NOTIFICATIONS_PATH;
        try {
            NotificationReceipt receipt = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(NotificationReceipt.class);
            if (receipt == null) {
                throw new NotificationDeliveryException("Notification service returned an empty response.", true, null);
            }
            log.debug("Notification service accepted {} (duplicate={})", request.idempotencyKey(), receipt.duplicate());
            return receipt;
        } catch (HttpServerErrorException ex) {
            throw new NotificationDeliveryException(
                    "Notification service returned " + ex.getStatusCode().value() + ".", true, ex);
        } catch (HttpClientErrorException ex) {
            throw new NotificationDeliveryException(
                    "Notification service rejected the request with " + ex.getStatusCode().value() + ".", false, ex);
        } catch (ResourceAccessException ex) {
            String reason = hasCause(ex, HttpTimeoutException.class)
                    ? "Notification service did not answer within " + properties.timeout().toMillis() + " ms."
                    : "Notification service could not be reached.";
            throw new NotificationDeliveryException(reason, true, ex);
        } catch (RestClientException ex) {
            throw new NotificationDeliveryException("Notification call failed: " + ex.getClass().getSimpleName(), true, ex);
        }
    }

    /**
     * The configured service URL, or this application's own address when the mock service is used.
     * The port is looked up per call because a test server's random port is only known after startup.
     */
    private String baseUrl() {
        String configured = properties.baseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.endsWith("/") ? configured.substring(0, configured.length() - 1) : configured;
        }
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        return "http://localhost:" + port;
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
