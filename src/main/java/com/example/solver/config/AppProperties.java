package com.example.solver.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
private String name;
private String regNo;
private String email;
private String generateWebhookUrl;
private String fallbackSubmitUrl;
private String finalQuery;


public String getName() { return name; }
public void setName(String name) { this.name = name; }
public String getRegNo() { return regNo; }
public void setRegNo(String regNo) { this.regNo = regNo; }
public String getEmail() { return email; }
public void setEmail(String email) { this.email = email; }
public String getGenerateWebhookUrl() { return generateWebhookUrl; }
public void setGenerateWebhookUrl(String generateWebhookUrl) { this.generateWebhookUrl = generateWebhookUrl; }
public String getFallbackSubmitUrl() { return fallbackSubmitUrl; }
public void setFallbackSubmitUrl(String fallbackSubmitUrl) { this.fallbackSubmitUrl = fallbackSubmitUrl; }
public String getFinalQuery() { return finalQuery; }
public void setFinalQuery(String finalQuery) { this.finalQuery = finalQuery; }
}
