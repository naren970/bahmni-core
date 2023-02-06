package org.bahmni.module.bahmnicore.service.impl;

import org.apache.commons.collections.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bahmni.module.bahmnicore.contract.drugorder.ConceptData;
import org.bahmni.module.bahmnicore.contract.drugorder.DrugOrderConfigResponse;
import org.bahmni.module.bahmnicore.contract.drugorder.OrderFrequencyData;
import org.bahmni.module.bahmnicore.dao.OrderDao;
import org.bahmni.module.bahmnicore.service.BahmniDrugOrderService;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.bahmni.module.bahmnicore.service.BahmniProgramWorkflowService;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.DrugOrder;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderFrequency;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniDrugOrder;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniOrderAttribute;
import org.openmrs.module.bahmniemrapi.drugorder.mapper.BahmniDrugOrderMapper;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.emrapi.encounter.ConceptMapper;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.emrapi.utils.HibernateLazyLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.Arrays;
import java.util.Comparator;

import static org.bahmni.module.bahmnicore.util.BahmniDateUtil.convertUTCToGivenFormat;

@Service
public class BahmniDrugOrderServiceImpl implements BahmniDrugOrderService {
    private ConceptService conceptService;
    private OrderService orderService;
    private PatientService openmrsPatientService;
    private OrderDao orderDao;
    private ConceptMapper conceptMapper = new ConceptMapper();
    private BahmniProgramWorkflowService bahmniProgramWorkflowService;
    private BahmniDrugOrderMapper bahmniDrugOrderMapper;
    private BahmniObsService bahmniObsService;

    private static final String GP_DOSING_INSTRUCTIONS_CONCEPT_UUID = "order.dosingInstructionsConceptUuid";
    private static Logger logger = LogManager.getLogger(BahmniDrugOrderService.class);
    private final static String SMS_TIMEZONE = "bahmni.sms.timezone";

    @Autowired
    public BahmniDrugOrderServiceImpl(ConceptService conceptService, OrderService orderService, PatientService patientService, OrderDao orderDao,
                                      BahmniProgramWorkflowService bahmniProgramWorkflowService, BahmniObsService bahmniObsService) {
        this.conceptService = conceptService;
        this.orderService = orderService;
        this.openmrsPatientService = patientService;
        this.orderDao = orderDao;
        this.bahmniProgramWorkflowService = bahmniProgramWorkflowService;
        this.bahmniDrugOrderMapper = new BahmniDrugOrderMapper();
        this.bahmniObsService = bahmniObsService;
    }


    @Override
    public List<DrugOrder> getActiveDrugOrders(String patientUuid) {
        return getActiveDrugOrders(patientUuid, new Date(), null, null, null, null, null);
    }

    @Override
    public List<DrugOrder> getActiveDrugOrders(String patientUuid, Date startDate, Date endDate) {
        return getActiveDrugOrders(patientUuid, new Date(), null, null, startDate, endDate, null);
    }

    @Override
    public List<DrugOrder> getPrescribedDrugOrders(List<String> visitUuids, String patientUuid, Boolean includeActiveVisit, Integer numberOfVisits, Date startDate, Date endDate, Boolean getEffectiveOrdersOnly) {
        if(CollectionUtils.isNotEmpty(visitUuids)) {
            return orderDao.getPrescribedDrugOrders(visitUuids);
        } else {
            Patient patient = openmrsPatientService.getPatientByUuid(patientUuid);
            return orderDao.getPrescribedDrugOrders(patient, includeActiveVisit, numberOfVisits, startDate, endDate, getEffectiveOrdersOnly);
        }
    }

    public Map<String,DrugOrder> getDiscontinuedDrugOrders(List<DrugOrder> drugOrders){
        return orderDao.getDiscontinuedDrugOrders(drugOrders);
    }

    @Override
    public List<DrugOrder> getInactiveDrugOrders(String patientUuid, Set<Concept> concepts, Set<Concept> drugConceptsToBeExcluded,
                                                 Collection<Encounter> encounters) {
        Patient patient = openmrsPatientService.getPatientByUuid(patientUuid);
        CareSetting careSettingByName = orderService.getCareSettingByName(CareSetting.CareSettingType.OUTPATIENT.toString());
        Date asOfDate = new Date();
        List<Order> orders = orderDao.getInactiveOrders(patient, orderService.getOrderTypeByName("Drug order"),
                careSettingByName, asOfDate, concepts, drugConceptsToBeExcluded, encounters);
        return mapOrderToDrugOrder(orders);
    }

    @Override
    public List<BahmniDrugOrder> getDrugOrders(String patientUuid, Boolean isActive, Set<Concept> drugConceptsToBeFiltered,
                                               Set<Concept> drugConceptsToBeExcluded, String patientProgramUuid) throws ParseException {
        Collection<Encounter> programEncounters = null;
        if (patientProgramUuid != null) {
            programEncounters = bahmniProgramWorkflowService.getEncountersByPatientProgramUuid(patientProgramUuid);
            if(programEncounters.isEmpty()){
                return new ArrayList<>();
            }
        }
        List<DrugOrder> drugOrders;

        if (isActive == null) {
            List<Order> orders = getAllDrugOrders(patientUuid, null, drugConceptsToBeFiltered, drugConceptsToBeExcluded, programEncounters);
            drugOrders = mapOrderToDrugOrder(orders);
        } else if (isActive) {
            drugOrders = getActiveDrugOrders(patientUuid, new Date(), drugConceptsToBeFiltered, drugConceptsToBeExcluded, null, null, programEncounters);
        } else {
            drugOrders = getInactiveDrugOrders(patientUuid, drugConceptsToBeFiltered, drugConceptsToBeExcluded, programEncounters);
        }

        Map<String, DrugOrder> discontinuedDrugOrderMap = getDiscontinuedDrugOrders(drugOrders);
        try {
            return bahmniDrugOrderMapper.mapToResponse(drugOrders, null, discontinuedDrugOrderMap, null);
        } catch (IOException e) {
            logger.error("Could not parse dosing instructions", e);
            throw new RuntimeException("Could not parse dosing instructions", e);

        }
    }

    @Override
    public List<DrugOrder> getPrescribedDrugOrdersForConcepts(Patient patient, Boolean includeActiveVisit, List<Visit> visits, List<Concept> concepts, Date startDate, Date endDate) {
        if( concepts == null || concepts.isEmpty()){
            return new ArrayList<>();
        }
        return orderDao.getPrescribedDrugOrdersForConcepts(patient, includeActiveVisit, visits, concepts, startDate, endDate);
    }

    @Override
    public DrugOrderConfigResponse getConfig() {
        DrugOrderConfigResponse response = new DrugOrderConfigResponse();
        response.setFrequencies(getFrequencies());
        response.setRoutes(mapConcepts(orderService.getDrugRoutes()));
        response.setDoseUnits(mapConcepts(orderService.getDrugDosingUnits()));
        response.setDurationUnits(mapConcepts(orderService.getDurationUnits()));
        response.setDispensingUnits(mapConcepts(orderService.getDrugDispensingUnits()));
        response.setDosingInstructions(mapConcepts(getSetMembersOfConceptSetFromGP(GP_DOSING_INSTRUCTIONS_CONCEPT_UUID)));
        response.setOrderAttributes(fetchOrderAttributeConcepts());
        return response;
    }

    @Override
    public List<Order> getAllDrugOrders(String patientUuid, String patientProgramUuid, Set<Concept> conceptsForDrugs,
                                        Set<Concept> drugConceptsToBeExcluded, Collection<Encounter> encounters) throws ParseException {
        Patient patientByUuid = openmrsPatientService.getPatientByUuid(patientUuid);
        OrderType orderTypeByUuid = orderService.getOrderTypeByUuid(OrderType.DRUG_ORDER_TYPE_UUID);
        if (patientProgramUuid != null) {
            return orderDao.getOrdersByPatientProgram(patientProgramUuid, orderTypeByUuid, conceptsForDrugs);
        }
        return orderDao.getAllOrders(patientByUuid, orderTypeByUuid, conceptsForDrugs, drugConceptsToBeExcluded, encounters);
    }

    @Override
    public List<BahmniDrugOrder> getSortedBahmniDrugOrdersForVisit(String patientUuid, String visitUuid) {
        List<DrugOrder> drugOrderList = getPrescribedDrugOrders(Arrays.asList(visitUuid), patientUuid, null,null, null, null, null);
        List<BahmniDrugOrder> bahmniDrugOrderList = getBahmniDrugOrdersForVisit(patientUuid, drugOrderList);
        Collections.sort(bahmniDrugOrderList, new Comparator<BahmniDrugOrder>() {
            @Override
            public int compare(BahmniDrugOrder o1, BahmniDrugOrder o2) {
                return o1.getEffectiveStartDate().compareTo(o2.getEffectiveStartDate());
            }
        });
        return bahmniDrugOrderList;
    }

    @Override
    public Map<BahmniDrugOrder, Integer> getMergedDrugOrderMap(List<BahmniDrugOrder> drugOrderList) {
        Map<BahmniDrugOrder, Integer> mergedDrugOrderMap = new LinkedHashMap<>();
        for(BahmniDrugOrder drugOrder : drugOrderList) {
            BahmniDrugOrder foundDrugOrder = mergedDrugOrderMap.entrySet().stream()
                    .map(x -> x.getKey())
                    .filter( existingOrder ->
                            areValuesEqual(existingOrder.getDrugNonCoded(), drugOrder.getDrugNonCoded()) &&
                                    (existingOrder.getDrug()!=null && drugOrder.getDrug()!=null &&
                                            areValuesEqual(existingOrder.getDrug().getUuid(), drugOrder.getDrug().getUuid())) &&
                                    areValuesEqual(existingOrder.getInstructions(), drugOrder.getInstructions()) &&
                                    compareDosingInstruction(existingOrder.getDosingInstructions(), drugOrder.getDosingInstructions()) &&
                                    areValuesEqual(existingOrder.getDosingInstructions().getRoute(), drugOrder.getDosingInstructions().getRoute()) &&
                                    areValuesEqual(existingOrder.getDosingInstructions().getAdministrationInstructions(), drugOrder.getDosingInstructions().getAdministrationInstructions()) &&
                                    areValuesEqual(existingOrder.getDosingInstructions().getAsNeeded(), drugOrder.getDosingInstructions().getAsNeeded()) &&
                                    areValuesEqual(existingOrder.getDateStopped(), drugOrder.getDateStopped()) &&
                                    getDateDifferenceInDays(existingOrder.getEffectiveStopDate(), drugOrder.getEffectiveStartDate()) <= 1.0 )
                    .findFirst()
                    .orElse(null);
            if (foundDrugOrder!=null) {
                mergedDrugOrderMap.put(foundDrugOrder, mergedDrugOrderMap.get(foundDrugOrder)+drugOrder.getDuration());
            } else {
                mergedDrugOrderMap.put(drugOrder, drugOrder.getDuration());
            }
        }
        return mergedDrugOrderMap;
    }

    @Override
    public String getPrescriptionAsString(Map<BahmniDrugOrder, Integer> drugOrderDurationMap, Locale locale) {
        String prescriptionString = "";
        int counter = 1;
        for (Map.Entry<BahmniDrugOrder, Integer> entry : drugOrderDurationMap.entrySet()) {
            prescriptionString += counter++ + ". " + getDrugOrderString(entry.getKey(), drugOrderDurationMap.get(entry.getKey()), locale) + "\n";
        }
        return prescriptionString;
    }

    @Override
    public String getAllProviderAsString(List<BahmniDrugOrder> drugOrders) {
        Set providerSet = new LinkedHashSet();
        for (BahmniDrugOrder drugOrder : drugOrders) {
            providerSet.add("Dr " + drugOrder.getProvider().getName());
        }
        return StringUtils.collectionToCommaDelimitedString(providerSet);
    }

    private String getDrugOrderString(BahmniDrugOrder drugOrder, Integer duration, Locale locale) {
        String drugOrderString = drugOrder.getDrug().getName();
        drugOrderString += ", " + (drugOrder.getDosingInstructions().getDose().intValue()) + " " + drugOrder.getDosingInstructions().getDoseUnits();
        drugOrderString += ", " + drugOrder.getDosingInstructions().getFrequency() + "-" + duration.toString() + " " + drugOrder.getDurationUnits();
        drugOrderString += ", start from " + convertUTCToGivenFormat(drugOrder.getEffectiveStartDate(), "dd-MM-yyyy", Context.getMessageSourceService().getMessage(SMS_TIMEZONE, null, locale));
        if(drugOrder.getDateStopped() != null)
            drugOrderString += ", stopped on " + convertUTCToGivenFormat(drugOrder.getDateStopped(), "dd-MM-yyyy", Context.getMessageSourceService().getMessage(SMS_TIMEZONE, null, locale));
        return drugOrderString;
    }

    private Boolean areValuesEqual(Object value1, Object value2) {
        if(value1 != null && value2 != null) {
            return value1.equals(value2);
        }
        return (value1 == null && value2 == null);
    };

    private Boolean compareDosingInstruction(EncounterTransaction.DosingInstructions value1, EncounterTransaction.DosingInstructions value2) {
        String doseAndFrequency1 = value1.getDose() + " " + value1.getDoseUnits() + ", " + value1.getFrequency();
        String doseAndFrequency2 = value2.getDose() + " " + value2.getDoseUnits() + ", " + value2.getFrequency();
        return doseAndFrequency1.equals(doseAndFrequency2);
    };

    private double getDateDifferenceInDays(Date date1, Date date2){
        long diff = date2.getTime() - date1.getTime();
        return (diff / (1000*60*60*24));
    }

    private Collection<Concept> getOrdAttributeConcepts() {
        Concept orderAttribute = conceptService.getConceptByName(BahmniOrderAttribute.ORDER_ATTRIBUTES_CONCEPT_SET_NAME);
        return orderAttribute == null ? Collections.EMPTY_LIST : orderAttribute.getSetMembers();
    }

    private List<BahmniDrugOrder> getBahmniDrugOrdersForVisit(String patientUuid, List<DrugOrder> drugOrders) {
        Map<String, DrugOrder> drugOrderMap = getDiscontinuedDrugOrders(drugOrders);
        try {
            Collection<BahmniObservation> orderAttributeObs = bahmniObsService.observationsFor(patientUuid, getOrdAttributeConcepts(), null, null, false, null, null, null);
            return bahmniDrugOrderMapper.mapToResponse(drugOrders, orderAttributeObs, drugOrderMap , null);
        } catch (IOException e) {
            logger.error("Could not parse drug order", e);
            throw new RuntimeException("Could not parse drug order", e);
        }
    }

    private List<EncounterTransaction.Concept> fetchOrderAttributeConcepts() {
        Concept orderAttributesConceptSet = conceptService.getConceptByName(BahmniOrderAttribute.ORDER_ATTRIBUTES_CONCEPT_SET_NAME);
        if(orderAttributesConceptSet != null){
            List<EncounterTransaction.Concept> etOrderAttributeConcepts = new ArrayList<>();
            List<Concept> orderAttributes = orderAttributesConceptSet.getSetMembers();
            for (Concept orderAttribute : orderAttributes) {
                etOrderAttributeConcepts.add(conceptMapper.map(orderAttribute));
            }
            return etOrderAttributeConcepts;
        }
        return Collections.EMPTY_LIST;
    }

    private List<Concept> getSetMembersOfConceptSetFromGP(String globalProperty) {
        String conceptUuid = Context.getAdministrationService().getGlobalProperty(globalProperty);
        Concept concept = Context.getConceptService().getConceptByUuid(conceptUuid);
        if (concept != null && concept.isSet()) {
            return concept.getSetMembers();
        }
        return Collections.emptyList();
    }

    private List<ConceptData> mapConcepts(List<Concept> drugDosingUnits) {
        return drugDosingUnits.stream().map((concept) -> new ConceptData(concept))
                .collect(Collectors.toList());
    }

    private List<OrderFrequencyData> getFrequencies() {
        List<OrderFrequency> orderFrequencies = orderService.getOrderFrequencies(false);
        return orderFrequencies.stream().map((orderFrequency) -> new OrderFrequencyData(orderFrequency))
                .collect(Collectors.toList());
    }

    private List<DrugOrder> getActiveDrugOrders(String patientUuid, Date asOfDate, Set<Concept> conceptsToFilter,
                                                Set<Concept> conceptsToExclude, Date startDate, Date endDate, Collection<Encounter> encounters) {
        Patient patient = openmrsPatientService.getPatientByUuid(patientUuid);
        CareSetting careSettingByName = orderService.getCareSettingByName(CareSetting.CareSettingType.OUTPATIENT.toString());
        List<Order> orders = orderDao.getActiveOrders(patient, orderService.getOrderTypeByName("Drug order"),
                careSettingByName, asOfDate, conceptsToFilter, conceptsToExclude, startDate, endDate, encounters);
        return mapOrderToDrugOrder(orders);
    }

    private List<DrugOrder> mapOrderToDrugOrder(List<Order> orders){
        HibernateLazyLoader hibernateLazyLoader = new HibernateLazyLoader();
        List<DrugOrder> drugOrders = new ArrayList<>();
        for(Order order: orders){
            order = hibernateLazyLoader.load(order);
            if(order instanceof DrugOrder) {
                drugOrders.add((DrugOrder) order);
            }
        }
        return drugOrders;
    }

}
