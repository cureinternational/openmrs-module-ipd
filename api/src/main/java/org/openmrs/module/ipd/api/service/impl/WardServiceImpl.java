package org.openmrs.module.ipd.api.service.impl;

import org.openmrs.Location;
import org.openmrs.Provider;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.dao.WardDAO;
import org.openmrs.module.ipd.api.model.AdmittedPatient;
import org.openmrs.module.ipd.api.model.WardPatientsSummary;
import org.openmrs.module.ipd.api.service.WardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Transactional
public class WardServiceImpl implements WardService {

    private WardDAO wardDAO;

    public void setWardDAO(WardDAO wardDAO) {
        this.wardDAO = wardDAO;
    }

    @Override
    public WardPatientsSummary getIPDWardPatientSummary(String wardUuid, String providerUuid) {
        Location location= Context.getService(LocationService.class).getLocationByUuid(wardUuid);
        Provider provider = Context.getProviderService().getProviderByUuid(providerUuid);
        Date currentDateTime = new Date();
        return wardDAO.getWardPatientSummary(location, provider, currentDateTime);
    }

    @Override
    public List<AdmittedPatient> getWardPatientsByUuid(String wardUuid, String sortBy) {
        Location location= Context.getService(LocationService.class).getLocationByUuid(wardUuid);
        return wardDAO.getAdmittedPatients(location,null, null, sortBy);
    }

    @Override
    public List<AdmittedPatient> getPatientsByWardAndProvider(String wardUuid, String providerUuid, String sortBy) {
        Location location = Context.getService(LocationService.class).getLocationByUuid(wardUuid);
        Provider provider = Context.getProviderService().getProviderByUuid(providerUuid);
        Date currentDateTime = new Date();
        return wardDAO.getAdmittedPatients(location, provider, currentDateTime, sortBy);
    }

    @Override
    public List<AdmittedPatient> searchWardPatients(String wardUuid, List<String> searchKeys, String searchValue, String sortBy) {
        Location location= Context.getService(LocationService.class).getLocationByUuid(wardUuid);
        return  wardDAO.searchAdmittedPatients(location,searchKeys,searchValue,sortBy);
    }

    @Override
    public List<AdmittedPatient> getAdmittedPatients() {
        return wardDAO.getAdmittedPatients(null,null, null, null);
    }
}
