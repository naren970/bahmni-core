package org.bahmni.module.bahmnicore.service;

import org.openmrs.Patient;

import java.util.Date;

public interface SMSService {

    String getPrescriptionMessage(Object[] prescriptionArguments);

    Object sendSMS(String phoneNumber, String message);
}
