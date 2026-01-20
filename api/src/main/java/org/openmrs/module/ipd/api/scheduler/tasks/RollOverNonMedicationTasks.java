package org.openmrs.module.ipd.api.scheduler.tasks;

import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.events.IPDEventManager;
import org.openmrs.module.ipd.api.events.model.IPDEvent;
import org.openmrs.module.ipd.api.events.model.IPDEventType;
import org.openmrs.scheduler.tasks.AbstractTask;


public class RollOverNonMedicationTasks extends AbstractTask {

    @Override
    public void execute() {
        IPDEventManager eventManager = Context.getRegisteredComponents(IPDEventManager.class).get(0);
        IPDEventType eventType = eventManager.getEventTypeForEncounter(String.valueOf(IPDEventType.ROLLOVER_TASK));
        if (eventType != null) {
            IPDEvent ipdEvent = new IPDEvent(null, null, eventType);
            eventManager.processEvent(ipdEvent);
        }
    }

}