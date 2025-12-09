package org.openmrs.module.ipd.web.validators;

import org.apache.commons.lang.StringUtils;
import org.openmrs.module.ipd.web.contract.NoteAmendmentRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class NoteAmendmentRequestValidator implements Validator {
    @Override
    public boolean supports(Class<?> aClass) {
        return NoteAmendmentRequest.class.equals(aClass);
    }

    @Override
    public void validate(Object target, Errors errors) {
        NoteAmendmentRequest noteAmendmentRequest = (NoteAmendmentRequest) target;

        if (StringUtils.isBlank(noteAmendmentRequest.getAmendedByUuid())) {
            errors.reject("amendedByUuid must not be null or empty");
        }
    }
}
