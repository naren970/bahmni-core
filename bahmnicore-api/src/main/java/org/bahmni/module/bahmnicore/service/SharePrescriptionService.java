package org.bahmni.module.bahmnicore.service;

import org.bahmni.module.bahmnicore.contract.SMS.PrescriptionSMS;
import org.openmrs.annotation.Authorized;

public interface SharePrescriptionService {
    @Authorized({"app:clinical"})
    Object sendPresciptionSMS(PrescriptionSMS prescription);
}
