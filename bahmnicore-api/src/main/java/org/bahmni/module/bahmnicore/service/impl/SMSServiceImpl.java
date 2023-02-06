package org.bahmni.module.bahmnicore.service.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.bahmni.module.bahmnicore.contract.SMS.SMSRequest;
import org.bahmni.module.bahmnicore.service.BahmniDrugOrderService;
import org.bahmni.module.bahmnicore.service.SMSService;
import org.openmrs.Patient;

import org.openmrs.api.context.Context;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Date;
import java.util.Locale;

import static org.bahmni.module.bahmnicore.util.BahmniDateUtil.convertUTCToGivenFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;

@Service
public class SMSServiceImpl implements SMSService {
    private static Logger logger = LogManager.getLogger(BahmniDrugOrderService.class);
    private final static String PRESCRIPTION_SMS_TEMPLATE = "bahmni.prescriptionSMSTemplate";
    private final static String SMS_TIMEZONE = "bahmni.sms.timezone";
    private final static String SMS_URL = "bahmni.sms.url";

    @Autowired
    public SMSServiceImpl() {}

    @Override
    public Object sendSMS(String phoneNumber, String message) {
        try {
            SMSRequest smsRequest = new SMSRequest();
            smsRequest.setPhoneNumber(phoneNumber);
            smsRequest.setMessage(message);

            ObjectMapper Obj = new ObjectMapper();
            String jsonObject = Obj.writeValueAsString(smsRequest);
            StringEntity params = new StringEntity(jsonObject);

            HttpPost request = new HttpPost(Context.getMessageSourceService().getMessage(SMS_URL, null, new Locale("en")));
            request.addHeader("content-type", "application/json");
            request.setEntity(params);
            HttpClient httpClient = HttpClientBuilder.create().build();
            HttpResponse response = httpClient.execute(request);

            return response.getStatusLine();
        } catch (Exception e) {
            logger.error("Exception occured in sending sms ", e);
            throw new RuntimeException("Exception occured in sending sms ", e);
        }
    }

    @Override
    public String getPrescriptionMessage(String lang, Date visitDate, Patient patient, String location, String providerDetail, String prescriptionDetail) {
        String smsTimeZone = Context.getMessageSourceService().getMessage(SMS_TIMEZONE, null, new Locale(lang));
        String smsTemplate = Context.getAdministrationService().getGlobalProperty(PRESCRIPTION_SMS_TEMPLATE);
        Object[] arguments = {convertUTCToGivenFormat(visitDate, "dd-MM-yyyy", smsTimeZone),
                patient.getGivenName() + " " + patient.getFamilyName(), patient.getGender(), patient.getAge().toString(),
                providerDetail, location, prescriptionDetail};
        if (StringUtils.isBlank(smsTemplate)) {
            return Context.getMessageSourceService().getMessage(PRESCRIPTION_SMS_TEMPLATE, arguments, new Locale(lang)).replace("\\n", System.lineSeparator());
        } else {
            return new MessageFormat(smsTemplate).format(arguments).replace("\\n", System.lineSeparator());
        }
    }

}
