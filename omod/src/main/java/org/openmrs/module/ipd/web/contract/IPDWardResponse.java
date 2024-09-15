package org.openmrs.module.ipd.web.contract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.openmrs.module.bedmanagement.AdmissionLocation;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IPDWardResponse {
    private String uuid;
    private String name;

    public static IPDWardResponse createFrom (AdmissionLocation admissionLocation){
        return IPDWardResponse.builder().
                uuid(admissionLocation.getWard().getUuid()).
                name(admissionLocation.getWard().getName()).
                build();
    }

}
