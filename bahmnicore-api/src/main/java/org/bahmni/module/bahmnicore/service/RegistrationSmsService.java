package org.bahmni.module.bahmnicore.service;

import org.openmrs.api.context.UserContext;
import org.openmrs.module.emrapi.patient.PatientProfile;
import org.springframework.transaction.annotation.Transactional;

public interface RegistrationSmsService {
    @Transactional(readOnly = true)
    void sendRegistrationSMS(PatientProfile profile, String location, UserContext userContext);
}
