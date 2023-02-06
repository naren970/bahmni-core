package org.bahmni.module.bahmnicore.service.impl;

import org.bahmni.module.bahmnicore.contract.SMS.PrescriptionSMS;
import org.bahmni.module.bahmnicore.service.BahmniDrugOrderService;
import org.bahmni.module.bahmnicore.service.BahmniVisitService;
import org.bahmni.module.bahmnicore.service.SMSService;
import org.bahmni.test.builder.PersonBuilder;
import org.bahmni.test.builder.VisitBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.Visit;
import org.openmrs.Location;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;

import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniDrugOrder;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Context.class})
@PowerMockIgnore("javax.management.*")
public class SharePrescriptionServiceImplIT {

    @Mock
    BahmniDrugOrderService bahmniDrugOrderService;
    @Mock
    BahmniVisitService bahmniVisitService;
    @Mock
    SMSService smsService;
    @InjectMocks
    SharePrescriptionServiceImpl sharePrescriptionService;
    @Mock
    AdministrationService administrationService;
    @Mock
    MessageSourceService messageSourceService;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
    }

    @Test
    public void shouldCallSendSMSForSendingPrescriptionSMS() throws Exception {
        PrescriptionSMS prescriptionSMS = new PrescriptionSMS();
        prescriptionSMS.setVisitUuid("visit-uuid");
        prescriptionSMS.setLocale("en");
        Visit visit = createVisitForTest();
        String sampleSMSContent = "Date: 30-01-2023\n" +
                "Prescription For Patient: testPersonName null, M, 13 years.\n" +
                "Doctor: Superman (Bahmni)\n" +
                "1. Paracetamol 150 mg/ml, 50 ml, Immediately-1 Days, start from 31-01-2023";

        when(administrationService.getGlobalProperty("bahmni.prescriptionSMSTemplate")).thenReturn("Date: {0}\nPrescription For Patient: {1}, {2}, {3} years.\nDoctor: {4} ({5})\n{6}");
        when(messageSourceService.getMessage("bahmni.sms.timezone", null, new Locale("en"))).thenReturn("IST");
        when(messageSourceService.getMessage("bahmni.sms.url", null, new Locale("en"))).thenReturn(null);
        when(bahmniVisitService.getVisitSummary(prescriptionSMS.getVisitUuid())).thenReturn(visit);
        when(bahmniVisitService.getParentLocationNameForVisit(visit.getLocation())).thenReturn(visit.getLocation().getName());
        when(bahmniDrugOrderService.getMergedDrugOrderMap(new ArrayList<BahmniDrugOrder>())).thenReturn(null);
        when(bahmniDrugOrderService.getAllProviderAsString(new ArrayList<BahmniDrugOrder>())).thenReturn("Superman");
        when(bahmniDrugOrderService.getPrescriptionAsString(null, new Locale("en"))).thenReturn("1. Paracetamol 150 mg/ml, 50 ml, Immediately-1 Days, start from 31-01-2023");
        when(smsService.getPrescriptionMessage(prescriptionSMS.getLocale(), visit.getStartDatetime(), visit.getPatient(),
                "Bahmni", "Superman", "1. Paracetamol 150 mg/ml, 50 ml, Immediately-1 Days, start from 31-01-2023"))
                .thenReturn(sampleSMSContent);
        sharePrescriptionService.sendPresciptionSMS(prescriptionSMS);
        verify(smsService, times(1)).sendSMS("+919999999999", sampleSMSContent);
    }

    private Visit createVisitForTest() throws Exception {
        Date visitDate = new SimpleDateFormat("MMMM d, yyyy", new Locale("en")).parse("January 30, 2023");
        Date birthDate = new SimpleDateFormat("MMMM d, yyyy", new Locale("en")).parse("January 30, 2010");
        Person person = new PersonBuilder().withUUID("puuid").withPersonName("testPersonName").build();
        person.setGender("M");
        person.setBirthdate(birthDate);
        PersonAttribute pa = new PersonAttribute();
        pa.setValue("+919999999999");
        PersonAttributeType pat = new PersonAttributeType();
        pat.setName("phoneNumber");
        pa.setAttributeType(pat);
        Set<PersonAttribute> paSet = new HashSet<>();
        paSet.add(pa);
        person.setAttributes(paSet);

        Visit visit = new VisitBuilder().withPerson(person).withUUID("visit-uuid").withStartDatetime(visitDate).build();
        Location location = new Location();
        location.setName("Bahmni");
        visit.setLocation(location);

        return visit;
    }

}
