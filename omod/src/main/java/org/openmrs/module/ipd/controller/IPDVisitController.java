package org.openmrs.module.ipd.controller;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.model.ServiceType;
import org.openmrs.module.ipd.api.model.Slot;
import org.openmrs.module.ipd.contract.IPDDrugOrderResponse;
import org.openmrs.module.ipd.contract.IPDTreatmentsResponse;
import org.openmrs.module.ipd.contract.MedicationAdministrationResponse;
import org.openmrs.module.ipd.model.IPDDrugOrder;
import org.openmrs.module.ipd.service.IPDVisitService;
import org.openmrs.module.ipd.util.PrivilegeConstants;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ipdVisit/{visitUuid}")
@Slf4j
public class IPDVisitController extends BaseRestController {

    private IPDVisitService ipdVisitService;

    @Autowired
    public IPDVisitController(IPDVisitService ipdVisitService) {
        this.ipdVisitService = ipdVisitService;
    }

    @RequestMapping(value = "/medication", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Object> getVisitWiseMedications (
            @PathVariable("visitUuid") String visitUuid,
            @RequestParam(value = "includes", required = false) List<String> includes) throws ParseException {
            if (!Context.getUserContext().hasPrivilege(PrivilegeConstants.GET_MEDICATION_ADMINISTRATION) || !Context.getUserContext().hasPrivilege(PrivilegeConstants.GET_MEDICATION_TASKS)) {
                return new ResponseEntity<>(RestUtil.wrapErrorResponse(new Exception(), "User doesn't have the following privilege(s) " + PrivilegeConstants.GET_MEDICATION_ADMINISTRATION + ", " + PrivilegeConstants.GET_MEDICATION_TASKS), FORBIDDEN);
            }
            List<IPDDrugOrder> prescribedOrders = ipdVisitService.getPrescribedOrders(visitUuid, true, null, null, null, false);
            List<IPDDrugOrderResponse> prescribedOrderResponse = prescribedOrders.stream().map(IPDDrugOrderResponse::createFrom).collect(Collectors.toList());
            List<MedicationAdministrationResponse> emergencyMedications = null;
            if (includes != null && includes.contains("emergencyMedications")) {
                List<Slot> emergencyMedicationSlots = ipdVisitService.getMedicationSlots(visitUuid, ServiceType.EMERGENCY_MEDICATION_REQUEST);
                emergencyMedications = emergencyMedicationSlots.stream().map(slot -> MedicationAdministrationResponse.createFrom(slot.getMedicationAdministration())).collect(Collectors.toList());
            }
            return new ResponseEntity(IPDTreatmentsResponse.createFrom(prescribedOrderResponse, emergencyMedications), OK);
    }
}
