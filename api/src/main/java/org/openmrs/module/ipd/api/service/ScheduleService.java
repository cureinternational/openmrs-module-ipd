package org.openmrs.module.ipd.api.service;

import org.openmrs.Visit;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.springframework.stereotype.Service;


@Service
public interface ScheduleService extends OpenmrsService {
	
//	@Authorized({ PrivilegeConstants.EDIT_IPD_SCHEDULES })
	Schedule getSchedule(Integer scheduleId) throws APIException;

//	@Authorized({ PrivilegeConstants.EDIT_IPD_SCHEDULES })
	Schedule saveSchedule(Schedule schedule) throws APIException;

	Schedule getScheduleByVisit(Visit visit);
}
