package org.openmrs.module.ipd.api.service;

import org.openmrs.api.APIException;
import org.openmrs.api.OpenmrsService;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.ipd.api.model.Reference;
import org.openmrs.module.ipd.api.model.Slot;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public interface ReferenceService extends OpenmrsService {

	//	@Authorized({ PrivilegeConstants.EDIT_IPD_SCHEDULES })
	Optional<Reference> getReferenceByTypeAndTargetUUID(String type, String targetUuid) throws APIException;

	//	@Authorized({ PrivilegeConstants.EDIT_IPD_SCHEDULES })
	Reference saveReference(Reference reference) throws APIException;
}
