package org.openmrs.module.ipd.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.apiext.FhirMedicationAdministrationService;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationAcknowledgementDao;
import org.openmrs.module.fhir2.apiext.dao.FhirMedicationAdministrationDao;
import org.openmrs.module.ipd.api.model.MedicationAdministrationAcknowledgement;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.ipd.api.service.SlotService;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementRequest;
import org.openmrs.module.ipd.web.contract.MedicationAdministrationAcknowledgementResponse;
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
    private final FhirMedicationAdministrationService fhirMedicationAdministrationService;
    private final FhirMedicationAdministrationAcknowledgementDao acknowledgementDao;
    private static final Logger log = LoggerFactory.getLogger(IPDMedicationAdministrationController.class);

    @Autowired
    public IPDMedicationAdministrationController(IPDMedicationAdministrationService ipdMedicationAdministrationService,
                                                 SlotService slotService,
                                                 FhirMedicationAdministrationDao medicationAdministrationDao,
                                                 MedicationAdministrationFactory medicationAdministrationFactory,
                                                 FhirMedicationAdministrationService fhirMedicationAdministrationService,
                                                 FhirMedicationAdministrationAcknowledgementDao acknowledgementDao) {
        this.ipdMedicationAdministrationService = ipdMedicationAdministrationService;
        this.medicationAdministrationFactory = medicationAdministrationFactory;
        this.fhirMedicationAdministrationService = fhirMedicationAdministrationService;
        this.acknowledgementDao = acknowledgementDao;
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

            MedicationAdministrationNote note = fhirMedicationAdministrationService.amendNote(
                    medicationAdministrationUuid,
                    noteRequest.getText(),
                    noteRequest.getReason()
            );

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
            if (!Context.getUserContext().hasPrivilege("Approve Amendment Note")) {
                return new ResponseEntity<>(RestUtil.wrapErrorResponse(new Exception(), "User doesn't have the following privilege: Approve Amendment Note"), FORBIDDEN);
            }

            MedicationAdministrationAcknowledgement acknowledgement = fhirMedicationAdministrationService.acknowledge(
                    medicationAdministrationUuid,
                    ackRequest.getRemarks()
            );

            MedicationAdministrationAcknowledgementResponse response = MedicationAdministrationAcknowledgementResponse.builder()
                    .uuid(acknowledgement.getUuid())
                    .actionType(acknowledgement.getActionType())
                    .actionDatetime(acknowledgement.getActionDatetime())
                    .acknowledgedBy(acknowledgement.getProvider() != null ?
                            acknowledgement.getProvider().getName() : null)
                    .acknowledgedByUuid(acknowledgement.getProvider() != null ?
                            acknowledgement.getProvider().getUuid() : null)
                    .remarks(acknowledgement.getRemarks())
                    .build();

            return new ResponseEntity<>(response, OK);
        } catch (Exception e) {
            log.error("Runtime error while trying to acknowledge medication administration", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), BAD_REQUEST);
        }
    }

}
