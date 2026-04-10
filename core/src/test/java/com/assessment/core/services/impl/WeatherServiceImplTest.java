package com.assessment.core.services.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assessment.core.config.WeatherConfig;
import com.assessment.core.models.WeatherData;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeatherServiceImplTest {

    private static final String CITY = "Bogota";
    private static final String TEMP = "11 C";
    private static final String WIND = "4 km/h";
    private static final String DESCRIPTION = "Cloudy";

    private TestWeatherService service;
    private Resource contextResource;

    @BeforeEach
    void setUp() {
        service = new TestWeatherService();
        service.activate(config(1000, 1000, 60));
        contextResource = mock(Resource.class);
    }

    @Test
    void cachesForecast() {
        WeatherData first = service.getForecast(CITY, contextResource);
        WeatherData second = service.getForecast(CITY, contextResource);

        assertEquals(1, service.fetchCount);
        assertSame(first, second);
        assertEquals(TEMP, first.getTemperature());
        assertEquals(WIND, first.getWind());
        assertEquals(DESCRIPTION, first.getDescription());
    }

    @Test
    void returnsNullWithoutConfig() {
        service.config = null;

        WeatherData result = service.getForecast(CITY, contextResource);

        assertNull(result);
        assertEquals(0, service.fetchCount);
    }

    @Test
    void returnsNullWhenFetchFails() {
        service.fail = true;

        WeatherData result = service.getForecast(CITY, contextResource);

        assertNull(result);
        assertEquals(1, service.fetchCount);
    }

    @Test
    void returnsCacheWhenRefreshFails() {
        service.activate(config(1000, 1000, 0));

        WeatherData first = service.getForecast(CITY, contextResource);
        service.fail = true;

        WeatherData second = service.getForecast(CITY, contextResource);

        assertEquals(2, service.fetchCount);
        assertSame(first, second);
        assertEquals(TEMP, second.getTemperature());
        assertEquals(DESCRIPTION, second.getDescription());
    }

    @Test
    void separatesCacheByConfigRoot() {
        service.config = new WeatherConfig("secret", "https://example.test/weather/%s", "/conf/assessment");
        WeatherData first = service.getForecast(CITY, contextResource);

        service.config = new WeatherConfig("secret", "https://example.test/weather/%s", "/conf/other");
        WeatherData second = service.getForecast(CITY, contextResource);

        assertEquals(2, service.fetchCount);
        assertEquals(TEMP, first.getTemperature());
        assertEquals(TEMP, second.getTemperature());
    }

    @Test
    void usesDefaultCity() {
        service.getForecast("   ", contextResource);

        assertEquals(1, service.fetchCount);
        assertEquals(CITY, service.lastCity);
    }

    private static final class TestWeatherService extends WeatherServiceImpl {
        private int fetchCount;
        private boolean fail;
        private String lastCity;
        private WeatherConfig config = new WeatherConfig("secret", "https://example.test/weather/%s",
                "/conf/assessment");

        @Override
        protected WeatherConfig resolveConfig(Resource contextResource) {
            return config;
        }

        @Override
        protected CompletableFuture<WeatherData> fetchWeatherDataAsync(String city, WeatherConfig weatherConfig) {
            fetchCount++;
            lastCity = city;

            if (fail) {
                return CompletableFuture.failedFuture(new IOException("boom"));
            }

            return CompletableFuture.completedFuture(new WeatherData(city, TEMP, WIND, DESCRIPTION));
        }
    }

    private static WeatherServiceImpl.Config config(int connectTimeoutMs, int readTimeoutMs, int cacheTtlSeconds) {
        WeatherServiceImpl.Config config = mock(WeatherServiceImpl.Config.class);
        when(config.default_endpoint()).thenReturn("https://goweather.xyz/weather/%s");
        when(config.api_key()).thenReturn("secret");
        when(config.connect_timeout_ms()).thenReturn(connectTimeoutMs);
        when(config.read_timeout_ms()).thenReturn(readTimeoutMs);
        when(config.cache_ttl_seconds()).thenReturn(cacheTtlSeconds);
        return config;
    }
}
