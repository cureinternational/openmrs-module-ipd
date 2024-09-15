package org.openmrs.module.ipd.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.ipd.api.service.SlotService;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationResponse;
import org.openmrs.module.ipd.web.factory.MedicationAdministrationFactory;
import org.openmrs.module.ipd.web.service.IPDMedicationAdministrationService;
import org.openmrs.module.ipd.web.util.PrivilegeConstants;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ipd")
@Slf4j
public class IPDMedicationAdministrationController extends BaseRestController {

    private final IPDMedicationAdministrationService ipdMedicationAdministrationService;
    private final MedicationAdministrationFactory medicationAdministrationFactory;
    private static final Logger log = LoggerFactory.getLogger(IPDMedicationAdministrationController.class);

    @Autowired
    public IPDMedicationAdministrationController(IPDMedicationAdministrationService ipdMedicationAdministrationService,
                                                 SlotService slotService,
                                                 FhirMedicationAdministrationDao medicationAdministrationDao,
                                                 MedicationAdministrationFactory medicationAdministrationFactory) {
        this.ipdMedicationAdministrationService = ipdMedicationAdministrationService;
        this.medicationAdministrationFactory = medicationAdministrationFactory;
    }

    @RequestMapping(value = "/scheduledMedicationAdministrations", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> createScheduledMedicationAdministration(@RequestBody List<MedicationAdministrationRequest> medicationAdministrationRequestList) {
        try {
            if (!Context.getUserContext().hasPrivilege(PrivilegeConstants.EDIT_MEDICATION_ADMINISTRATION)) {
                return new ResponseEntity<>(RestUtil.wrapErrorResponse(new Exception(), "User doesn't have the following privilege " + PrivilegeConstants.EDIT_MEDICATION_ADMINISTRATION), FORBIDDEN);
            }
            List<MedicationAdministrationResponse> medicationAdministrationResponseList = new ArrayList<>();
            for (MedicationAdministrationRequest medicationAdministrationRequest : medicationAdministrationRequestList) {
                MedicationAdministration medicationAdministration = ipdMedicationAdministrationService.saveScheduledMedicationAdministration(medicationAdministrationRequest);
                medicationAdministrationResponseList.add(medicationAdministrationFactory.mapMedicationAdministrationToResponse(medicationAdministration));
            }
            return new ResponseEntity<>(medicationAdministrationResponseList, OK);
        } catch (Exception e) {
            log.error("Runtime error while trying to create new medicationAdministration", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/adhocMedicationAdministrations", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> createAdhocMedicationAdministration(@RequestBody MedicationAdministrationRequest medicationAdministrationRequest) {
        try {
            if (!Context.getUserContext().hasPrivilege(PrivilegeConstants.EDIT_ADHOC_MEDICATION_TASKS)) {
                return new ResponseEntity<>(RestUtil.wrapErrorResponse(new Exception(), "User doesn't have the following privilege(s) " + PrivilegeConstants.EDIT_ADHOC_MEDICATION_TASKS), FORBIDDEN);
            }
            MedicationAdministration medicationAdministration = ipdMedicationAdministrationService.saveAdhocMedicationAdministration(medicationAdministrationRequest);
            MedicationAdministrationResponse medicationAdministrationResponse = medicationAdministrationFactory.mapMedicationAdministrationToResponse(medicationAdministration);
            return new ResponseEntity<>(medicationAdministrationResponse, OK);
        } catch (Exception e) {
            log.error("Runtime error while trying to create new medicationAdministration", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/adhocMedicationAdministrations/{medicationAdministrationUuid}", method = RequestMethod.PUT)
    @ResponseBody
    public ResponseEntity<Object> updateAdhocMedicationAdministration(
            @PathVariable("medicationAdministrationUuid") String medicationAdministrationUuid,
            @RequestBody MedicationAdministrationRequest medicationAdministrationRequest) {
        try {
            MedicationAdministration medicationAdministration = ipdMedicationAdministrationService.updateAdhocMedicationAdministration(medicationAdministrationUuid,medicationAdministrationRequest);
            return new ResponseEntity(medicationAdministrationFactory.mapMedicationAdministrationToResponse(medicationAdministration),OK);
        } catch (Exception e) {
            log.error("Runtime error while trying to update new medicationAdministration", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), BAD_REQUEST);
        }
    }

}
