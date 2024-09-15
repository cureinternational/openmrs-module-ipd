package org.openmrs.module.ipd.web.service;


import org.openmrs.module.ipd.api.model.IPDPatientDetails;
import org.openmrs.module.ipd.api.model.WardPatientsSummary;

import java.util.List;

public interface IPDWardService {

    WardPatientsSummary getIPDWardPatientSummary(String wardUuid, String providerUuid);

    IPDPatientDetails getIPDPatientByWard(String wardUuid, Integer offset, Integer limit, String sortBy);

    IPDPatientDetails searchIPDPatientsInWard(String wardUuid, List<String> searchKeys, String searchValue, Integer offset, Integer limit, String sortBy);

    IPDPatientDetails getIPDPatientsByWardAndProvider(String wardUuid, String providerUuid, Integer offset, Integer limit, String sortBy);
}
