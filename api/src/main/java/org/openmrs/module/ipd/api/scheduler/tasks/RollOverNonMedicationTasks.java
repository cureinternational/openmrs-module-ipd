package org.openmrs.module.ipd.api.scheduler.tasks;

import org.openmrs.api.context.Context;
import org.openmrs.module.ipd.api.events.IPDEventManager;
import org.openmrs.module.ipd.api.events.model.IPDEvent;
import org.openmrs.module.ipd.api.events.model.IPDEventType;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class RollOverNonMedicationTasks extends AbstractTask implements ApplicationContextAware {

    @Override
    public void execute() {
        IPDEventManager eventManager = Context.getRegisteredComponents(IPDEventManager.class).get(0);
        IPDEventType eventType = eventManager.getEventTypeForEncounter(String.valueOf(IPDEventType.ROLLOVER_TASK));
        if (eventType != null) {
            IPDEvent ipdEvent = new IPDEvent(null, null, eventType);
            eventManager.processEvent(ipdEvent);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        // Implement if needed for Spring context injection
    }

}