//package org.bahmni.module.bahmnicore.service.impl;
//
//
//import org.bahmni.module.bahmnicore.properties.BahmniCoreProperties;
//import org.springframework.stereotype.Component;
//
//@Component
//public class ConnectionDetails {
//    private static final String AUTH_URI = "openmrs.auth.uri";
//    private static final String OPENMRS_USER = "openmrs.user";
//    private static final String OPENMRS_PASSWORD = "openmrs.password";
//    private static final String OPENMRS_WEBCLIENT_CONNECT_TIMEOUT = "openmrs.connectionTimeoutInMilliseconds";
//    private static final String OPENMRS_WEBCLIENT_READ_TIMEOUT = "openmrs.replyTimeoutInMilliseconds";
//
//    public static org.bahmni.webclients.ConnectionDetails get() {
//        return new org.bahmni.webclients.ConnectionDetails(
//                BahmniCoreProperties.getProperty(AUTH_URI),
//                BahmniCoreProperties.getProperty(OPENMRS_USER),
//                BahmniCoreProperties.getProperty(OPENMRS_PASSWORD),
//                Integer.parseInt(BahmniCoreProperties.getProperty(OPENMRS_WEBCLIENT_CONNECT_TIMEOUT)),
//                Integer.parseInt(BahmniCoreProperties.getProperty(OPENMRS_WEBCLIENT_READ_TIMEOUT)));
//    }
//}
