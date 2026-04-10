package com.assessment.core.models;

import com.assessment.core.services.WeatherService;
import com.day.cq.wcm.api.Page;
import javax.annotation.PostConstruct;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.injectorspecific.OSGiService;
import org.apache.sling.models.annotations.injectorspecific.ScriptVariable;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Model(adaptables = SlingHttpServletRequest.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class WeatherModel {

    private static final Logger LOG = LoggerFactory.getLogger(WeatherModel.class);

    @ValueMapValue
    private String city;

    @ScriptVariable
    private Page currentPage;

    @OSGiService
    private WeatherService weatherService;

    @SlingObject
    private Resource resource;

    private WeatherData weatherData;

    @PostConstruct
    protected void init() {
        if (!isCityConfigured()) {
            weatherData = null;
            return;
        }

        try {
            weatherData = weatherService != null ? weatherService.getForecast(getCity(), resource) : null;
        } catch (Exception e) {
            LOG.warn("Could not initialize weather model for {}", resource != null ? resource.getPath() : "<null>", e);
            weatherData = null;
        }
    }

    public String getCity() {
        return city != null ? city.trim() : "";
    }

    public boolean isCityConfigured() {
        return !getCity().isEmpty();
    }

    public WeatherData getWeatherData() {
        return weatherData;
    }

    public boolean isConfigured() {
        return weatherData != null;
    }

    public String getPageTitle() {
        return currentPage != null ? currentPage.getTitle() : "Weather Page";
    }
}
