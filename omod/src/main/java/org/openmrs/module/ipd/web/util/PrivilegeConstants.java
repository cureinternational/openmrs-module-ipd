package org.openmrs.module.ipd.web.util;

import org.openmrs.annotation.AddOnStartup;

public class PrivilegeConstants {

    @AddOnStartup(description = "Edit Medication Tasks description")
    public static final String EDIT_MEDICATION_TASKS = "Edit Medication Tasks";
    @AddOnStartup(description = "Delete Medication Tasks description")
    public static final String DELETE_MEDICATION_TASKS = "Delete Medication Tasks";
    @AddOnStartup(description = "Edit adhoc medication tasks description")
    public static final String EDIT_ADHOC_MEDICATION_TASKS = "Edit adhoc medication tasks";
    public static final String EDIT_MEDICATION_ADMINISTRATION = org.openmrs.module.medicationadministration.util.PrivilegeConstants.EDIT_MEDICATION_ADMINISTRATION;
    public static final String GET_MEDICATION_ADMINISTRATIONS = org.openmrs.module.medicationadministration.util.PrivilegeConstants.GET_MEDICATION_ADMINISTRATIONS;
    @AddOnStartup(description = "Get Medication Tasks description")
    public static final String GET_MEDICATION_TASKS = "Get Medication Tasks";
    @AddOnStartup(description = "Approve Amend Note description")
    public static final String APPROVE_AMEND_NOTE = "Approve Amend Note";
}
