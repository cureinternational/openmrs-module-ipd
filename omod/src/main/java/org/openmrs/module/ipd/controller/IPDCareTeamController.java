package org.openmrs.module.ipd.controller;

import lombok.extern.slf4j.Slf4j;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.contract.CareTeamRequest;
import org.openmrs.module.ipd.contract.CareTeamResponse;
import org.openmrs.module.ipd.contract.ScheduleMedicationResponse;
import org.openmrs.module.ipd.service.IPDCareTeamService;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;


@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/ipd/careteam")
@Slf4j
public class IPDCareTeamController extends BaseRestController {

    private final IPDCareTeamService ipdCareTeamService;

    @Autowired
    public IPDCareTeamController(IPDCareTeamService ipdCareTeamService) {
        this.ipdCareTeamService = ipdCareTeamService;
    }

    @RequestMapping(value = "/participants", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> createMedicationSchedule(@RequestBody CareTeamRequest careTeamRequest) {
        try {
            CareTeam careTeam = ipdCareTeamService.saveCareTeamParticipants(careTeamRequest);
            return new ResponseEntity<>(CareTeamResponse.createFrom(careTeam), OK);
        } catch (Exception e) {
            log.error("Runtime error while trying to create new schedule", e);
            return new ResponseEntity<>(RestUtil.wrapErrorResponse(e, e.getMessage()), BAD_REQUEST);
        }
    }

}
