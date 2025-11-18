package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.openmrs.module.ipd.api.model.MedicationAdministrationNote;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.representation.Representation;

import java.util.Date;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties
public class AmendmentNoteResponse {
    private String uuid;
    private String text;
    private String amendedText;
    private Object author;
    private Date recordedTime;
    private String amendedReason;
    private Object approvedBy;
    private String approvalStatus;
    private Date approvedDateTime;
    private String approvalNotes;

    public static AmendmentNoteResponse createFrom(MedicationAdministrationNote openmrsObject) {
        if (openmrsObject == null) {
            return null;
        }

        Object author = null;
        if (openmrsObject.getAuthor() != null) {
            author = ConversionUtil.convertToRepresentation(openmrsObject.getAuthor(), Representation.REF);
        }

        Object approvedBy = null;
        if (openmrsObject.getApprovedBy() != null) {
            approvedBy = ConversionUtil.convertToRepresentation(openmrsObject.getApprovedBy(), Representation.REF);
        }

        String approvalStatusName = null;
        if (openmrsObject.getApprovalStatus() != null) {
            approvalStatusName = openmrsObject.getApprovalStatus().name();
        }

        return AmendmentNoteResponse.builder()
                .uuid(openmrsObject.getUuid())
                .text(openmrsObject.getText())
                .amendedText(openmrsObject.getAmendedText())
                .author(author)
                .recordedTime(openmrsObject.getRecordedTime())
                .amendedReason(openmrsObject.getAmendedReason())
                .approvedBy(approvedBy)
                .approvalStatus(approvalStatusName)
                .approvedDateTime(openmrsObject.getApprovedDateTime())
                .approvalNotes(openmrsObject.getApprovalNotes())
                .build();
    }
}
