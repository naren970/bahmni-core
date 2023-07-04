package org.bahmni.module.bahmnicore.service;


import org.bahmni.webclients.ClientCookies;

public interface OpenmrsLogin {


    void getConnection();
    ClientCookies getCookies();

}
