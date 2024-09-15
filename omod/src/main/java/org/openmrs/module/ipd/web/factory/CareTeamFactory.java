package org.openmrs.module.ipd.web.factory;

import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.model.CareTeam;
import org.openmrs.module.ipd.api.model.CareTeamParticipant;
import org.openmrs.module.ipd.api.util.DateTimeUtil;
import org.openmrs.module.ipd.web.contract.CareTeamParticipantRequest;
import org.openmrs.module.ipd.web.contract.CareTeamRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CareTeamFactory {


    public CareTeam createCareTeamFromRequest(CareTeamRequest request, Patient patient, Visit visit) {
        CareTeam careTeam = new CareTeam();
        careTeam.setVisit(visit);
        careTeam.setPatient(patient);
        careTeam.setStartTime(visit.getStartDatetime());

        if (request.getCareTeamParticipantsRequest() !=null){
            List<CareTeamParticipant> careTeamParticipants = new ArrayList<>();
            for (CareTeamParticipantRequest participantRequest: request.getCareTeamParticipantsRequest()) {
                CareTeamParticipant participant = new CareTeamParticipant();
                participant.setStartTime(DateTimeUtil.convertEpochTimeToDate(participantRequest.getStartTime()));
                participant.setEndTime(DateTimeUtil.convertEpochTimeToDate(participantRequest.getEndTime()));
                participant.setProvider(Context.getProviderService().getProviderByUuid(participantRequest.getProviderUuid()));
                careTeamParticipants.add(participant);
            }
            careTeam.setParticipants(new HashSet<>(careTeamParticipants));
        }
        return careTeam;
    }

    public CareTeam updateCareTeamFromRequest(CareTeamRequest careTeamRequest, CareTeam careTeam) {
        if (careTeamRequest.getCareTeamParticipantsRequest() !=null){
            List<CareTeamParticipant> careTeamParticipants = new ArrayList<>();
            for (CareTeamParticipantRequest participantRequest: careTeamRequest.getCareTeamParticipantsRequest()) {
                if (participantRequest.getUuid()!=null){
                    CareTeamParticipant careTeamParticipant = careTeam.getParticipants().stream()
                            .filter(participant -> participant.getUuid().equals(participantRequest.getUuid()))
                            .findFirst().get();
                    if (participantRequest.getVoided() != null) careTeamParticipant.setVoided(participantRequest.getVoided());
                }
                else {
                    Boolean participantAlreadyExists = careTeam.getParticipants().stream().
                            anyMatch(participant -> participant.getVoided()!=true
                                    && DateTimeUtil.convertEpochTimeToDate(participantRequest.getStartTime()).equals(participant.getStartTime()));
                    if (participantAlreadyExists) throw new APIException("Participant Already exists for given time frame");
                    CareTeamParticipant participant = new CareTeamParticipant();
                    participant.setStartTime(DateTimeUtil.convertEpochTimeToDate(participantRequest.getStartTime()));
                    participant.setEndTime(DateTimeUtil.convertEpochTimeToDate(participantRequest.getEndTime()));
                    participant.setProvider(Context.getProviderService().getProviderByUuid(participantRequest.getProviderUuid()));
                    careTeam.getParticipants().add(participant);
                }
            }
        }
        return careTeam;
    }
}
