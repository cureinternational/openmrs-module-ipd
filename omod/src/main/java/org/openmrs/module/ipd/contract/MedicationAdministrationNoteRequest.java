package org.openmrs.module.ipd.contract;

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
@JsonIgnoreProperties
public class MedicationAdministrationNoteRequest {
    private String uuid;
    private String authorUuid;
    private Long recordedTime;
    private String text;

    public Date getRecordedTimeAsLocaltime() {
        return this.recordedTime != null ? new Date(TimeUnit.SECONDS.toMillis(this.recordedTime)): new Date();
    }
}
