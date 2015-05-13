package org.openmrs.module.bahmnicore.web.v1_0.controller;

import org.apache.commons.lang3.ObjectUtils;
import org.bahmni.module.bahmnicore.service.BahmniObsService;
import org.openmrs.Concept;
import org.openmrs.Visit;
import org.openmrs.api.ConceptService;
import org.openmrs.api.VisitService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/bahmnicore/observations")
public class BahmniObservationsController extends BaseRestController {

    private static final String LATEST = "latest";
    private static final String INITIALLATEST = "initiallatest";
    private BahmniObsService bahmniObsService;
    private ConceptService conceptService;
    private VisitService visitService;

    @Autowired
    public BahmniObservationsController(BahmniObsService bahmniObsService, ConceptService conceptService, VisitService visitService) {
        this.bahmniObsService = bahmniObsService;
        this.conceptService = conceptService;
        this.visitService = visitService;
    }

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public Collection<BahmniObservation> get(@RequestParam(value = "patientUuid", required = true) String patientUUID,
                                       @RequestParam(value = "concept", required = true) List<String> rootConceptNames,
                                       @RequestParam(value = "scope", required = false) String scope,
                                       @RequestParam(value = "numberOfVisits", required = false) Integer numberOfVisits) {

        List<Concept> rootConcepts = new ArrayList<>();
        for (String rootConceptName : rootConceptNames) {
            rootConcepts.add(conceptService.getConceptByName(rootConceptName));
        }

        if (ObjectUtils.notEqual(scope, LATEST) && ObjectUtils.notEqual(scope, INITIALLATEST) ) {
            return bahmniObsService.observationsFor(patientUUID, rootConcepts, numberOfVisits);
        }

        Collection<BahmniObservation> observations = bahmniObsService.getLatest(patientUUID, rootConcepts, numberOfVisits);
        if (ObjectUtils.equals(scope, INITIALLATEST)) {
            Collection<BahmniObservation> initialObsByVisit = bahmniObsService.getInitial(patientUUID, rootConcepts, numberOfVisits);
            return getNonRedundantObs(observations, initialObsByVisit);
        }

        return observations;
    }

    @RequestMapping(method = RequestMethod.GET,params = {"visitUuid"})
    @ResponseBody
    public Collection<BahmniObservation> get(@RequestParam(value = "visitUuid", required = true) String visitUuid,
                                             @RequestParam(value = "scope", required = false) String scope,
                                             @RequestParam(value = "concept", required = false) List<String> conceptNames){

        if (ObjectUtils.notEqual(scope, LATEST) && ObjectUtils.notEqual(scope, INITIALLATEST) ) {
            return bahmniObsService.getObservationForVisit(visitUuid, conceptNames);
        }

        Visit visit = visitService.getVisitByUuid(visitUuid);

        List<Concept> rootConcepts = new ArrayList<>();
        for (String rootConceptName : conceptNames) {
            rootConcepts.add(conceptService.getConceptByName(rootConceptName));
        }

        Collection<BahmniObservation> obsByVisit = bahmniObsService.getLatestObsByVisit(visit, rootConcepts);

        if (ObjectUtils.equals(scope, INITIALLATEST)) {
            Collection<BahmniObservation> initialObsByVisit = bahmniObsService.getInitialObsByVisit(visit, rootConcepts);
            return getNonRedundantObs(obsByVisit, initialObsByVisit);
        }
        return obsByVisit;
    }

    private ArrayList<BahmniObservation> getNonRedundantObs(Collection<BahmniObservation> obsByVisit, Collection<BahmniObservation> initialObsByVisit) {
        ArrayList<BahmniObservation> bahmniObservations = new ArrayList<>();
        bahmniObservations.addAll(obsByVisit);
        for (BahmniObservation observation : obsByVisit) {
            for (BahmniObservation initialObs : initialObsByVisit) {
                if(initialObs.getConceptNameToDisplay().equals(observation.getConceptNameToDisplay()) && (initialObs.getUuid() != observation.getUuid())) {
                    bahmniObservations.add(initialObs);
                }
            }
        }
        return bahmniObservations;
    }
}
