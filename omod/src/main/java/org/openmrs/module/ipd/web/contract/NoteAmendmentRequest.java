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
public class NoteAmendmentRequest {
    private String amendedText;
    private String amendedReason;
    private String amendedByUuid;
    private Long amendedDateTime;

    public Date getAmendedDateTimeAsLocaltime() {
        return this.amendedDateTime != null ? new Date(TimeUnit.SECONDS.toMillis(this.amendedDateTime)) : new Date();
    }
}
