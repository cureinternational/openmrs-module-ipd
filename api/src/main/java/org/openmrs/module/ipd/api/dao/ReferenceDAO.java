package org.openmrs.module.ipd.api.dao;

import org.openmrs.api.db.DAOException;
import org.openmrs.module.ipd.api.model.Reference;
import org.springframework.stereotype.Repository;

import java.util.Optional;


public interface ReferenceDAO {

	Optional<Reference> getReferenceByTypeAndTargetUUID(String type, String targetUUID) throws DAOException;

	Reference saveReference(Reference reference) throws DAOException;
}
