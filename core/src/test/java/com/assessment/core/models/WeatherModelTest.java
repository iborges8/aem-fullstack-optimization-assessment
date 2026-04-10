package com.assessment.core.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assessment.core.services.WeatherService;
import com.day.cq.wcm.api.Page;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.sling.api.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WeatherModelTest {

    private static final String CITY = "Bogota";

    private WeatherModel model;
    private WeatherService weatherService;
    private Resource resource;

    @BeforeEach
    void setUp() {
        model = new WeatherModel();
        weatherService = mock(WeatherService.class);
        resource = mock(Resource.class);
    }

    @Test
    void initLoadsWeatherData() throws Exception {
        when(weatherService.getForecast(CITY, resource)).thenReturn(new WeatherData(CITY, "11 C", "4 km/h", "Cloudy"));

        setField(model, "city", CITY);
        setField(model, "resource", resource);
        setField(model, "weatherService", weatherService);

        invokeInit(model);

        verify(weatherService).getForecast(CITY, resource);
        assertTrue(model.isConfigured());
        assertTrue(model.isCityConfigured());
        assertEquals(CITY, model.getCity());
        assertEquals("11 C", model.getWeatherData().getTemperature());
        assertEquals("4 km/h", model.getWeatherData().getWind());
        assertEquals("Cloudy", model.getWeatherData().getDescription());
    }

    @Test
    void initHandlesNullServiceResponse() throws Exception {
        when(weatherService.getForecast(CITY, resource)).thenReturn(null);

        setField(model, "city", CITY);
        setField(model, "resource", resource);
        setField(model, "weatherService", weatherService);

        invokeInit(model);

        verify(weatherService).getForecast(CITY, resource);
        assertFalse(model.isConfigured());
        assertTrue(model.isCityConfigured());
        assertEquals(CITY, model.getCity());
    }

    @Test
    void initSkipsServiceWhenCityMissing() throws Exception {
        setField(model, "city", null);
        setField(model, "resource", resource);
        setField(model, "weatherService", weatherService);

        invokeInit(model);

        verifyNoInteractions(weatherService);
        assertFalse(model.isConfigured());
        assertFalse(model.isCityConfigured());
        assertEquals("", model.getCity());
    }

    @Test
    void getPageTitleReturnsPageTitle() throws Exception {
        Page page = mock(Page.class);
        when(page.getTitle()).thenReturn("Assessment Home");

        setField(model, "currentPage", page);

        assertEquals("Assessment Home", model.getPageTitle());
    }

    private static void invokeInit(WeatherModel model) throws Exception {
        Method method = WeatherModel.class.getDeclaredMethod("init");
        method.setAccessible(true);
        method.invoke(model);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}