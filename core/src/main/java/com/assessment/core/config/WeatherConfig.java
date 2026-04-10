package com.assessment.core.config;

public class WeatherConfig {

    private final String apiKey;
    private final String endpoint;
    private final String configRoot;

    public WeatherConfig(String apiKey, String endpoint, String configRoot) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.configRoot = configRoot;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getConfigRoot() {
        return configRoot;
    }
}
