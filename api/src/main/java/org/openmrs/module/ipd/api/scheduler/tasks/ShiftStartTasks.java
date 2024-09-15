package org.openmrs.module.ipd.api.scheduler.tasks;

import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.events.IPDEventManager;
import org.openmrs.module.ipd.api.events.model.IPDEvent;
import org.openmrs.module.ipd.api.events.model.IPDEventType;
import org.openmrs.scheduler.tasks.AbstractTask;


@Component
public class ShiftStartTasks extends AbstractTask implements ApplicationContextAware {

    @Override
    public void execute() {
        IPDEventManager eventManager = Context.getRegisteredComponents(IPDEventManager.class).get(0);
        IPDEventType eventType = eventManager.getEventTypeForEncounter(String.valueOf(IPDEventType.SHIFT_START_TASK));
        if (eventType != null) {
            IPDEvent ipdEvent = new IPDEvent(null, null, eventType);
            eventManager.processEvent(ipdEvent);
        }
    }

}
