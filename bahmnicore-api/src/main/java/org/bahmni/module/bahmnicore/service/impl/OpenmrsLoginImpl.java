package org.bahmni.module.bahmnicore.service.impl;

import java.net.URI;
import java.net.URISyntaxException;

import org.bahmni.module.bahmnicore.service.OpenmrsLogin;
import org.bahmni.webclients.ClientCookies;
import org.bahmni.webclients.HttpClient;
import org.springframework.stereotype.Component;

@Component
public class OpenmrsLoginImpl implements OpenmrsLogin {
    private ClientCookies cookies;

    @Override
    public void getConnection() {
        HttpClient authenticatedWebClient = WebClientFactory.getClient();
        org.bahmni.webclients.ConnectionDetails connectionDetails = ConnectionDetails.get();
        String authUri = connectionDetails.getAuthUrl();
        getCookiesAfterConnection(authenticatedWebClient, authUri);
    }

    public void getCookiesAfterConnection(HttpClient authenticatedWebClient, String urlString) {
        try {
            this.cookies = authenticatedWebClient.getCookies(new URI(urlString));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Is not a valid URI - " + urlString);
        }
    }

    @Override
    public ClientCookies getCookies() {
        return cookies;
    }
}