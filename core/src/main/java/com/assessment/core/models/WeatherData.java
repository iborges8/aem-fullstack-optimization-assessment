package com.assessment.core.models;

public class WeatherData {

    private final String city;
    private final String temperature;
    private final String wind;
    private final String description;

    public WeatherData(String city, String temperature, String wind, String description) {
        this.city = city;
        this.temperature = temperature;
        this.wind = wind;
        this.description = description;
    }

    public String getCity() {
        return city;
    }

    public String getTemperature() {
        return temperature;
    }

    public String getWind() {
        return wind;
    }

    public String getDescription() {
        return description;
    }
}
