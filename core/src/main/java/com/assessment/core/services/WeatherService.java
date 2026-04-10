package com.assessment.core.services;

import com.assessment.core.models.WeatherData;
import org.apache.sling.api.resource.Resource;

public interface WeatherService {

    WeatherData getForecast(String city, Resource contextResource);
}
