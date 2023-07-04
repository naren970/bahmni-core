package org.bahmni.module.bahmnicore.service.impl;

import org.bahmni.webclients.ConnectionDetails;
import org.bahmni.webclients.HttpClient;
import org.bahmni.webclients.openmrs.OpenMRSLoginAuthenticator;
import org.springframework.stereotype.Component;

@Component
public class WebClientFactory {

    public static HttpClient getClient() {
        ConnectionDetails connectionDetails = org.bahmni.module.bahmnicore.service.impl.ConnectionDetails.get();
        return new HttpClient(connectionDetails, getAuthenticator(connectionDetails));
    }


    private static OpenMRSLoginAuthenticator getAuthenticator(ConnectionDetails connectionDetails) {
        return new OpenMRSLoginAuthenticator(connectionDetails);

    }
}
