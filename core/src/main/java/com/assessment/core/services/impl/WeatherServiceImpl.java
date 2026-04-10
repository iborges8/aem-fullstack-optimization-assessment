package com.assessment.core.services.impl;

import com.assessment.core.config.WeatherConfig;
import com.assessment.core.models.WeatherData;
import com.assessment.core.services.WeatherService;
import com.day.cq.commons.inherit.HierarchyNodeInheritanceValueMap;
import com.day.cq.commons.inherit.InheritanceValueMap;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = WeatherService.class)
@Designate(ocd = WeatherServiceImpl.Config.class)
public class WeatherServiceImpl implements WeatherService {

    // ------------------------
    // OSGi config
    // ------------------------
    @ObjectClassDefinition(name = "Assessment Weather Service")
    public @interface Config {
        @AttributeDefinition(name = "Default API endpoint")
        String default_endpoint() default "https://goweather.xyz/weather/%s";

        @AttributeDefinition(name = "API key")
        String api_key() default "";

        @AttributeDefinition(name = "Connect timeout in milliseconds")
        int connect_timeout_ms() default 2000;

        @AttributeDefinition(name = "Read timeout in milliseconds")
        int read_timeout_ms() default 3000;

        @AttributeDefinition(name = "Cache TTL in seconds")
        int cache_ttl_seconds() default 300;
    }

    private static final Logger LOG = LoggerFactory.getLogger(WeatherServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DEFAULT_CITY = "Bogota";
    private static final String DEFAULT_CACHE_KEY_ROOT = "default";
    private static final String CONF_PROPERTY = "cq:conf";
    private static final String CONFIG_RELATIVE_PATH = "/settings/cloudconfigs/weather";
    private static final String ENDPOINT_PROPERTY = "endpoint";

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<WeatherData>> inFlightRequests = new ConcurrentHashMap<>();

    private volatile String defaultEndpoint;
    private volatile String apiKey;
    private volatile HttpClient httpClient;
    private volatile int connectTimeoutMs;
    private volatile int readTimeoutMs;
    private volatile long cacheTtlMillis;

    // ------------------------
    // Activate
    // ------------------------
    @Activate
    protected void activate(Config config) {
        defaultEndpoint = trimToNull(config.default_endpoint());
        apiKey = trimToEmpty(config.api_key());
        connectTimeoutMs = Math.max(0, config.connect_timeout_ms());
        readTimeoutMs = Math.max(0, config.read_timeout_ms());
        cacheTtlMillis = Math.max(0L, config.cache_ttl_seconds() * 1000L);

        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        if (connectTimeoutMs > 0) {
            clientBuilder.connectTimeout(Duration.ofMillis(connectTimeoutMs));
        }

        httpClient = clientBuilder.build();
        cache.clear();
        inFlightRequests.clear();
    }

    // ------------------------
    // Public API
    // ------------------------
    @Override
    public WeatherData getForecast(String city, Resource contextResource) {
        String requestedCity = normalizeCity(city);
        WeatherConfig weatherConfig = resolveConfig(contextResource);

        if (weatherConfig == null || isBlank(weatherConfig.getEndpoint())) {
            LOG.warn("Weather configuration could not be resolved for {}", safePath(contextResource));
            return null;
        }

        String cacheKey = buildCacheKey(requestedCity, weatherConfig.getConfigRoot());
        CacheEntry cachedEntry = cache.get(cacheKey);

        if (cachedEntry != null && !cachedEntry.isExpired()) {
            return cachedEntry.weatherData;
        }

        if (cachedEntry != null) {
            refreshForecastAsync(cacheKey, requestedCity, weatherConfig);
            return cachedEntry.weatherData;
        }

        return loadForecast(cacheKey, requestedCity, weatherConfig);
    }

    // ------------------------
    // External request
    // ------------------------
    protected CompletableFuture<WeatherData> fetchWeatherDataAsync(String city, WeatherConfig weatherConfig) {
        HttpRequest request = buildRequest(buildEndpoint(city, weatherConfig));

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status < 200 || status >= 300) {
                        throw new CompletionException(
                                new IOException("Unexpected weather API response: " + status));
                    }

                    try {
                        return parseWeatherData(city, response.body());
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                });
    }

    // ------------------------
    // Weather configuration
    // ------------------------
    protected WeatherConfig resolveConfig(Resource contextResource) {
        if (contextResource == null) {
            return null;
        }

        Page page = getContainingPage(contextResource);
        if (page == null) {
            return null;
        }

        String confRoot = getConfRoot(page);
        if (isBlank(confRoot)) {
            return null;
        }

        ResourceResolver resourceResolver = contextResource.getResourceResolver();
        if (resourceResolver == null) {
            return null;
        }

        Resource configResource = resourceResolver.getResource(confRoot + CONFIG_RELATIVE_PATH);
        if (configResource == null) {
            return new WeatherConfig("", defaultEndpoint, confRoot);
        }

        ValueMap properties = configResource.getValueMap();
        String endpoint = trimToNull(properties.get(ENDPOINT_PROPERTY, String.class));

        return new WeatherConfig(apiKey, endpoint != null ? endpoint : defaultEndpoint, confRoot);
    }

    protected Page getContainingPage(Resource resource) {
        ResourceResolver resourceResolver = resource.getResourceResolver();
        PageManager pageManager = resourceResolver != null ? resourceResolver.adaptTo(PageManager.class) : null;
        return pageManager != null ? pageManager.getContainingPage(resource) : null;
    }

    protected String getConfRoot(Page page) {
        Resource contentResource = page.getContentResource();
        if (contentResource == null) {
            return null;
        }

        InheritanceValueMap inheritanceValueMap = new HierarchyNodeInheritanceValueMap(contentResource);
        String conf = inheritanceValueMap.getInherited(CONF_PROPERTY, String.class);
        return trimToNull(conf);
    }

    // ------------------------
    // Forecast loading
    // ------------------------
    private WeatherData loadForecast(String cacheKey, String city, WeatherConfig weatherConfig) {
        try {
            return awaitForecast(startFetch(cacheKey, city, weatherConfig), city);
        } catch (IOException e) {
            LOG.warn("Could not fetch weather data for city {}", city, e);
            return null;
        }
    }

    private void refreshForecastAsync(String cacheKey, String city, WeatherConfig weatherConfig) {
        startFetch(cacheKey, city, weatherConfig).whenComplete((weatherData, throwable) -> {
            if (throwable != null) {
                LOG.warn("Could not refresh weather data for city {}", city, unwrap(throwable));
            }
        });
    }

    private CompletableFuture<WeatherData> startFetch(String cacheKey, String city, WeatherConfig weatherConfig) {
        CompletableFuture<WeatherData> existing = inFlightRequests.get(cacheKey);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<WeatherData> created = fetchWeatherDataAsync(city, weatherConfig)
                .thenApply(weatherData -> cacheForecast(cacheKey, weatherData));

        CompletableFuture<WeatherData> previous = inFlightRequests.putIfAbsent(cacheKey, created);
        if (previous != null) {
            return previous;
        }

        created.whenComplete((result, throwable) -> inFlightRequests.remove(cacheKey, created));
        return created;
    }

    private WeatherData awaitForecast(CompletableFuture<WeatherData> future, String city) throws IOException {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Weather API request was interrupted for city " + city, e);
        } catch (ExecutionException e) {
            throw toIOException(e.getCause());
        }
    }

    private WeatherData cacheForecast(String cacheKey, WeatherData weatherData) {
        if (weatherData != null) {
            cache.put(cacheKey, new CacheEntry(weatherData, System.currentTimeMillis() + cacheTtlMillis));
        }
        return weatherData;
    }

    // ------------------------
    // HTTP helpers
    // ------------------------
    private HttpRequest buildRequest(String endpoint) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Accept", "application/json")
                .GET();

        if (readTimeoutMs > 0) {
            builder.timeout(Duration.ofMillis(readTimeoutMs));
        }

        return builder.build();
    }

    private String buildEndpoint(String city, WeatherConfig weatherConfig) {
        String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
        String endpoint = String.format(weatherConfig.getEndpoint(), encodedCity);

        String apiKey = trimToNull(weatherConfig.getApiKey());
        if (apiKey != null) {
            endpoint += endpoint.contains("?") ? "&" : "?";
            endpoint += "apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        }

        return endpoint;
    }

    private WeatherData parseWeatherData(String city, String payload) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(payload);

        return new WeatherData(
                city,
                root.path("temperature").asText(""),
                root.path("wind").asText(""),
                root.path("description").asText(""));
    }

    // ------------------------
    // Utility methods
    // ------------------------
    private String buildCacheKey(String city, String configRoot) {
        String root = trimToNull(configRoot);
        return (root != null ? root : DEFAULT_CACHE_KEY_ROOT) + "::" + city.toLowerCase(Locale.ROOT);
    }

    private String normalizeCity(String city) {
        String normalizedCity = trimToNull(city);
        return normalizedCity != null ? normalizedCity : DEFAULT_CITY;
    }

    private String safePath(Resource resource) {
        return resource != null ? resource.getPath() : "<null>";
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value != null ? value.trim() : "";
    }

    private IOException toIOException(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause instanceof IOException
                ? (IOException) cause
                : new IOException("Unexpected weather API error", cause);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            if (current.getCause() == null) {
                break;
            }
            current = current.getCause();
        }
        return current;
    }

    private static final class CacheEntry {
        private final WeatherData weatherData;
        private final long expiresAt;

        private CacheEntry(WeatherData weatherData, long expiresAt) {
            this.weatherData = Objects.requireNonNull(weatherData);
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
