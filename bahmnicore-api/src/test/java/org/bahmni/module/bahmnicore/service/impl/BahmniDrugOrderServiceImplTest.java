package org.bahmni.module.bahmnicore.service.impl;

import org.apache.commons.lang3.time.DateUtils;
import org.bahmni.module.bahmnicore.dao.OrderDao;
import org.bahmni.module.bahmnicore.service.BahmniProgramWorkflowService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openmrs.CareSetting;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.bahmniemrapi.drugorder.contract.BahmniDrugOrder;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Context.class})
public class BahmniDrugOrderServiceImplTest {

    public static final String PATIENT_PROGRAM_UUID = "patient-program-uuid";
    public static final String PATIENT_UUID = "patient-uuid";

    @Mock
    BahmniProgramWorkflowService bahmniProgramWorkflowService;
    @Mock
    PatientService patientService;
    @Mock
    OrderService orderService;
    @Mock
    OrderDao orderDao;
    @Mock
    MessageSourceService messageSourceService;

    @InjectMocks
    BahmniDrugOrderServiceImpl bahmniDrugOrderService;
    private final CareSetting mockCareSetting = mock(CareSetting.class);
    private final Patient mockPatient = mock(Patient.class);
    private final OrderType mockOrderType = mock(OrderType.class);
    private HashSet<Concept> conceptsToFilter;
    private final ArgumentCaptor<Date> dateArgumentCaptor = ArgumentCaptor.forClass(Date.class);
    private final List<Encounter> encounters = new ArrayList<>();


    @Before
    public void setUp() throws Exception {
        initMocks(this);
        encounters.add(new Encounter());

        when(bahmniProgramWorkflowService.getEncountersByPatientProgramUuid(PATIENT_PROGRAM_UUID)).thenReturn(encounters);
        when(patientService.getPatientByUuid(PATIENT_UUID)).thenReturn(mockPatient);
        when(orderService.getCareSettingByName(anyString())).thenReturn(mockCareSetting);
        when(orderService.getOrderTypeByName("Drug order")).thenReturn(mockOrderType);
        when(orderService.getOrderTypeByUuid(OrderType.DRUG_ORDER_TYPE_UUID)).thenReturn(mockOrderType);

        final Concept concept = mock(Concept.class);
        conceptsToFilter = new HashSet<Concept>() {{
            add(concept);
        }};

    }

    @Test
    public void shouldGetActiveDrugOrdersOfAPatientProgram() throws ParseException {
        when(orderDao.getActiveOrders(any(Patient.class), any(OrderType.class), any(CareSetting.class),
                dateArgumentCaptor.capture(), anySet(), eq(null), eq(null), eq(null), anyCollection())).thenReturn(new ArrayList<Order>());

       bahmniDrugOrderService.getDrugOrders(PATIENT_UUID, true, conceptsToFilter, null, PATIENT_PROGRAM_UUID);

        final Date value = dateArgumentCaptor.getValue();
        verify(orderDao).getActiveOrders(mockPatient, mockOrderType, mockCareSetting, value, conceptsToFilter, null, null, null, encounters);
    }

    @Test
    public void shouldReturnEmptyListWhenNoEncountersAssociatedWithPatientProgram() throws ParseException {
        when(bahmniProgramWorkflowService.getEncountersByPatientProgramUuid(PATIENT_PROGRAM_UUID)).thenReturn(new HashSet<Encounter>());

        final List<BahmniDrugOrder> drugOrders = bahmniDrugOrderService.getDrugOrders(PATIENT_UUID, true, null, null, PATIENT_PROGRAM_UUID);

        verifyNoMoreInteractions(orderDao);
        assertTrue(drugOrders.isEmpty());
    }

    @Test
    public void shouldGetAllDrugOrdersOfAPatientProgram() throws ParseException {
        bahmniDrugOrderService.getDrugOrders(PATIENT_UUID, null, conceptsToFilter, null, PATIENT_PROGRAM_UUID);

        verify(orderDao).getAllOrders(mockPatient, mockOrderType, conceptsToFilter, null, encounters);
    }

    @Test
    public void shouldNotConsiderEncountersToFetchDrugOrdersIfPatientProgramUuidIsNull() throws Exception {
        bahmniDrugOrderService.getDrugOrders(PATIENT_UUID, null, conceptsToFilter, null, null);
        List<Encounter> encounters = null ;

        verify(orderDao).getAllOrders(mockPatient, mockOrderType,conceptsToFilter, null, encounters);
        verifyNoMoreInteractions(bahmniProgramWorkflowService);
    }

    @Test
    public void shouldReturnMergedDrugOrderAsMap() throws Exception {
        List<BahmniDrugOrder> bahmniDrugOrderList = buildBahmniDrugOrderList();
        Map<BahmniDrugOrder, String> mergedDrugOrderMap = bahmniDrugOrderService.getMergedDrugOrderMap(bahmniDrugOrderList);
        Map<BahmniDrugOrder, String> expectedMergedDrugOrderMap = new LinkedHashMap<>();
        expectedMergedDrugOrderMap.put(bahmniDrugOrderList.get(0), "10 Days");
        expectedMergedDrugOrderMap.put(bahmniDrugOrderList.get(2), "3 Days");
        assertEquals(expectedMergedDrugOrderMap, mergedDrugOrderMap);
    }

    @Test
    public void shouldReturnPrescriptionAsString() throws Exception {
        mockStatic(Context.class);
        when(Context.getMessageSourceService()).thenReturn(messageSourceService);
        when(messageSourceService.getMessage("bahmni.sms.timezone", null, new Locale("en"))).thenReturn("IST");
        when(messageSourceService.getMessage("bahmni.sms.dateformat", null, new Locale("en"))).thenReturn("dd-MM-yyyy");
        Date drugOrderStartDate = new SimpleDateFormat("MMMM d, yyyy", new Locale("en")).parse("January 30, 2023");
        EncounterTransaction.DrugOrder etDrugOrder = createETDrugOrder("1", "Paracetamol", 2.0, "Once a day", drugOrderStartDate, 5);
        BahmniDrugOrder bahmniDrugOrder = createBahmniDrugOrder(null, etDrugOrder);
        Map<BahmniDrugOrder, String> drugOrderDurationMap = new LinkedHashMap<>();
        drugOrderDurationMap.put(bahmniDrugOrder, "10 Days");
        String prescriptionString = bahmniDrugOrderService.getPrescriptionAsString(drugOrderDurationMap, new Locale("en"));
        String expectedPrescriptionString = "1. Paracetamol, 2 tab (s), Once a day-10 Days, start from 30-01-2023\n";
        assertEquals(expectedPrescriptionString, prescriptionString);
    }

    @Test
    public void shouldReturnAllUniqueProviderNames() throws Exception {
        List<BahmniDrugOrder> bahmniDrugOrderList = buildBahmniDrugOrderList();
        String providerString = StringUtils.collectionToCommaDelimitedString(bahmniDrugOrderService.getUniqueProviderNames(bahmniDrugOrderList));
        String expectedProviderString = "Dr Harry,Dr Grace";
        assertEquals(expectedProviderString, providerString);
    }

    private List<BahmniDrugOrder> buildBahmniDrugOrderList() {
        List<BahmniDrugOrder> bahmniDrugOrderList = new ArrayList<>();
        try {
            EncounterTransaction.Provider provider = createETProvider("1", "Harry");
            Date drugOrderStartDate = new SimpleDateFormat("MMMM d, yyyy", new Locale("en")).parse("January 30, 2023");
            EncounterTransaction.DrugOrder etDrugOrder = createETDrugOrder("1", "Paracetamol", 2.0, "Once a day", drugOrderStartDate, 5);
            BahmniDrugOrder bahmniDrugOrder = createBahmniDrugOrder(provider, etDrugOrder);
            bahmniDrugOrderList.add(bahmniDrugOrder);

            provider = createETProvider("2", "Grace");
            drugOrderStartDate = DateUtils.addDays(drugOrderStartDate, 5);
            etDrugOrder = createETDrugOrder("1", "Paracetamol", 2.0, "Once a day", drugOrderStartDate, 5);
            bahmniDrugOrder = createBahmniDrugOrder(provider, etDrugOrder);
            bahmniDrugOrderList.add(bahmniDrugOrder);

            provider = createETProvider("1", "Harry");
            drugOrderStartDate = new SimpleDateFormat("MMMM d, yyyy", new Locale("en")).parse("January 30, 2023");
            etDrugOrder = createETDrugOrder("2", "Amoxicillin", 1.0, "Twice a day", drugOrderStartDate, 3);
            bahmniDrugOrder = createBahmniDrugOrder(provider, etDrugOrder);
            bahmniDrugOrderList.add(bahmniDrugOrder);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return bahmniDrugOrderList;
    }

    private BahmniDrugOrder createBahmniDrugOrder(EncounterTransaction.Provider provider, EncounterTransaction.DrugOrder etDrugOrder) {
        BahmniDrugOrder bahmniDrugOrder = new BahmniDrugOrder();
        bahmniDrugOrder.setDrugOrder(etDrugOrder);
        bahmniDrugOrder.setProvider(provider);
        return bahmniDrugOrder;
    }

    private EncounterTransaction.Provider createETProvider(String uuid, String name) {
        EncounterTransaction.Provider provider = new EncounterTransaction.Provider();
        provider.setUuid(uuid);
        provider.setName(name);
        return provider;
    }

    private EncounterTransaction.DrugOrder createETDrugOrder(String drugUuid, String drugName, Double dose, String frequency, Date effectiveStartDate, Integer duration) {
        EncounterTransaction.Drug encounterTransactionDrug = new EncounterTransaction.Drug();
        encounterTransactionDrug.setUuid(drugUuid);
        encounterTransactionDrug.setName(drugName);

        EncounterTransaction.DosingInstructions dosingInstructions = new EncounterTransaction.DosingInstructions();
        dosingInstructions.setAdministrationInstructions("{\"instructions\":\"As directed\"}");
        dosingInstructions.setAsNeeded(false);
        dosingInstructions.setDose(dose);
        dosingInstructions.setDoseUnits("tab (s)");
        dosingInstructions.setFrequency(frequency);
        dosingInstructions.setNumberOfRefills(0);
        dosingInstructions.setRoute("UNKNOWN");

        EncounterTransaction.DrugOrder drugOrder = new EncounterTransaction.DrugOrder();
        drugOrder.setOrderType("Drug Order");
        drugOrder.setDrug(encounterTransactionDrug);
        drugOrder.setDosingInstructions(dosingInstructions);
        drugOrder.setDuration(duration);
        drugOrder.setDurationUnits("Days");
        drugOrder.setEffectiveStartDate(effectiveStartDate);
        drugOrder.setEffectiveStopDate(DateUtils.addDays(effectiveStartDate, duration));
        drugOrder.setVoided(false);

        return drugOrder;
    }
}
