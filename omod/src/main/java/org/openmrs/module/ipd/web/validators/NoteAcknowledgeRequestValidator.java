package org.openmrs.module.ipd.web.validators;

import org.apache.commons.lang.StringUtils;
import org.openmrs.module.ipd.web.contract.NoteAcknowledgeRequest;
import org.openmrs.module.ipd.web.contract.NoteAmendmentRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class NoteAcknowledgeRequestValidator implements Validator {
    @Override
    public boolean supports(Class<?> aClass) {
        return NoteAcknowledgeRequest.class.equals(aClass);
    }

    @Override
    public void validate(Object target, Errors errors) {
        NoteAcknowledgeRequest noteAcknowledgeRequest = (NoteAcknowledgeRequest) target;

        if (StringUtils.isBlank(noteAcknowledgeRequest.getApprovedByUuid())) {
            errors.reject("approvedByUuid must not be null or empty");
        }
    }
}
