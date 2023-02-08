package org.bahmni.module.bahmnicore.service.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bahmni.module.bahmnicore.contract.SMS.PrescriptionSMS;
import org.bahmni.module.bahmnicore.service.BahmniDrugOrderService;
import org.bahmni.module.bahmnicore.service.BahmniVisitService;
import org.bahmni.module.bahmnicore.service.SMSService;
import org.bahmni.module.bahmnicore.service.SharePrescriptionService;
import org.openmrs.Visit;
import org.openmrs.annotation.Authorized;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniDrugOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SharePrescriptionServiceImpl implements SharePrescriptionService {
    private static Logger logger = LogManager.getLogger(BahmniDrugOrderService.class);
    private BahmniVisitService bahmniVisitService;
    private BahmniDrugOrderService drugOrderService;
    private SMSService smsService;

    @Autowired
    public SharePrescriptionServiceImpl(BahmniVisitService bahmniVisitService, BahmniDrugOrderService drugOrderService, SMSService smsService) {
        this.bahmniVisitService = bahmniVisitService;
        this.drugOrderService = drugOrderService;
        this.smsService = smsService;
    }

    @Override
    @Transactional(readOnly = true)
    @Authorized({"Send Prescription"})
    public Object sendPresciptionSMS(PrescriptionSMS prescription) {
        Visit visit = bahmniVisitService.getVisitSummary(prescription.getVisitUuid());
        String locationName = bahmniVisitService.getParentLocationForVisit(visit.getLocation()).getName();
        List<BahmniDrugOrder> drugOrderList = drugOrderService.getBahmniDrugOrdersForVisit(visit.getPatient().getUuid(), visit.getUuid());
        Map<BahmniDrugOrder, String> mergedDrugOrderMap = drugOrderService.getMergedDrugOrderMap(drugOrderList);
        String providerString = StringUtils.collectionToCommaDelimitedString(drugOrderService.getUniqueProviderNames(drugOrderList));
        String prescriptionString = drugOrderService.getPrescriptionAsString(mergedDrugOrderMap, new Locale(prescription.getLocale()));
        Object[] prescriptionArguments = {prescription.getLocale(), visit.getStartDatetime(), visit.getPatient(), locationName, providerString, prescriptionString};
        String prescriptionSMSContent = smsService.getPrescriptionMessage(prescriptionArguments);
        return smsService.sendSMS(visit.getPatient().getAttribute("phoneNumber").getValue(), prescriptionSMSContent);
    }

}
