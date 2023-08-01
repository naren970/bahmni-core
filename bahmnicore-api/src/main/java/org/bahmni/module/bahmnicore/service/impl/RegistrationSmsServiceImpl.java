package org.bahmni.module.bahmnicore.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bahmni.module.bahmnicore.service.RegistrationSmsService;
import org.bahmni.module.bahmnicore.service.SMSService;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.emrapi.patient.PatientProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrationSmsServiceImpl implements RegistrationSmsService {

    private SMSService smsService;
    private Log log = LogFactory.getLog(this.getClass());

    @Autowired
    public RegistrationSmsServiceImpl(SMSService smsService) {
        this.smsService = smsService;
    }

    @Override
    @Async("bahmniCoreAsync")
    public void sendRegistrationSMS(PatientProfile profile, String locationUuid, UserContext userContext) {
        Context.openSession();
        Context.setUserContext(userContext);
        if (Context.getUserContext().hasPrivilege("Send Registration SMS")){
        Patient patient = profile.getPatient();
        String phoneNumber = patient.getAttribute("phoneNumber").getValue();
        if (null == phoneNumber) {
            log.info("Since no mobile number found for the patient. SMS not sent.");
            return;
        }
        Location location = Context.getLocationService().getLocationByUuid(locationUuid);
        String message = smsService.getRegistrationMessage(new Locale("en"), patient, location);
        smsService.sendSMS(phoneNumber, message);}
        else
            log.info("SMS not sent because current user does not have the required privileges");
    }
}
