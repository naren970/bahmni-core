package org.bahmni.module.bahmnicore.service;

import org.openmrs.Patient;

import java.util.Date;

public interface SMSService {

    String getPrescriptionMessage(String lang, Date visitDate, Patient patient, String location, String providerDetail, String prescriptionDetail);

    Object sendSMS(String phoneNumber, String message);
}
