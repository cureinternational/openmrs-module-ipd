package org.openmrs.module.ipd.api.service.impl;

import org.openmrs.Visit;
import org.openmrs.module.ipd.api.dao.ScheduleDAO;
import org.openmrs.module.ipd.api.model.Schedule;
import org.openmrs.module.ipd.api.service.ScheduleService;
import org.openmrs.api.APIException;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScheduleServiceImpl extends BaseOpenmrsService implements ScheduleService {
	
	private static final Logger log = LoggerFactory.getLogger(ScheduleServiceImpl.class);
	
	private final ScheduleDAO scheduleDAO;

	@Autowired
	public ScheduleServiceImpl(ScheduleDAO scheduleDAO) {
		this.scheduleDAO = scheduleDAO;
	}

	@Override
	@Transactional(readOnly = true)
	public Schedule getSchedule(Integer scheduleId) throws APIException {
		return scheduleDAO.getSchedule(scheduleId);
	}
	
	@Override
	public Schedule saveSchedule(Schedule schedule) throws APIException {
		return scheduleDAO.saveSchedule(schedule);
	}

    @Override
    public Schedule getScheduleByVisit(Visit visit) {
		return scheduleDAO.getScheduleByVisit(visit);
    }
}
