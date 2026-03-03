package org.openmrs.module.ipd.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhirExtension.model.Task;
import org.openmrs.module.fhirExtension.web.mapper.TaskMapper;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationNoteResponse;
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
    private final TaskMapper taskMapper;
    private static final Logger log = LoggerFactory.getLogger(IPDMedicationAdministrationController.class);

    @Autowired
    public IPDMedicationAdministrationController(IPDMedicationAdministrationService ipdMedicationAdministrationService,
                                                 MedicationAdministrationFactory medicationAdministrationFactory,
                                                 TaskMapper taskMapper) {
        this.ipdMedicationAdministrationService = ipdMedicationAdministrationService;
        this.medicationAdministrationFactory = medicationAdministrationFactory;
        this.taskMapper = taskMapper;
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

    @RequestMapping(value = "/medicationadministration/{uuid}/note", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> addAmendmentNote(
            @PathVariable("uuid") String medicationAdministrationUuid,
            @RequestBody MedicationAdministrationNoteRequest noteRequest) {
        try {
            if (!Context.getUserContext().hasPrivilege(PrivilegeConstants.EDIT_MEDICATION_ADMINISTRATION)) {
                return new ResponseEntity<>(RestUtil.wrapErrorResponse(new Exception(), "User doesn't have the following privilege " + PrivilegeConstants.EDIT_MEDICATION_ADMINISTRATION), FORBIDDEN);
            }

            MedicationAdministrationNote note = ipdMedicationAdministrationService.amendNote(medicationAdministrationUuid, noteRequest);

            MedicationAdministrationNoteResponse response = MedicationAdministrationNoteResponse.createFrom(note);
            return new ResponseEntity<>(response, OK);
        } catch (Exception e) {
            log.error("Runtime error while trying to add amendment note to medication administration", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/medicationadministration/{uuid}/acknowledgement", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> acknowledgeMedicationAdministration(
            @PathVariable("uuid") String medicationAdministrationUuid,
            @RequestBody MedicationAdministrationAcknowledgementRequest ackRequest) {
        try {
            if (!Context.getUserContext().hasPrivilege(PrivilegeConstants.APPROVE_AMEND_NOTE)) {
                return new ResponseEntity<>(RestUtil.wrapErrorResponse(new Exception(), "User doesn't have the following privilege: " + PrivilegeConstants.APPROVE_AMEND_NOTE), FORBIDDEN);
            }

            Task task = ipdMedicationAdministrationService.acknowledge(medicationAdministrationUuid, ackRequest);
            return new ResponseEntity<>(taskMapper.constructResponse(task), OK);
        } catch (Exception e) {
            log.error("Runtime error while trying to acknowledge medication administration", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), BAD_REQUEST);
        }
    }

}
