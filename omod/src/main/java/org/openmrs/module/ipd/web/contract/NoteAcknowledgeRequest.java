package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NoteAcknowledgeRequest {
    private String approvalStatus;  // PENDING, APPROVED, REJECTED
    private Long approvedDateTime;
    private String approvalNotes;

    public Date getApprovedDateTimeAsLocaltime() {
        return this.approvedDateTime != null ? new Date(TimeUnit.SECONDS.toMillis(this.approvedDateTime)) : new Date();
    }
}
