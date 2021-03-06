/*
 * Copyright 2015 Collective, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.collective.celos.trigger;

import com.collective.celos.ScheduledTime;
import com.collective.celos.database.StateDatabaseConnection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Trigger that takes N nested triggers and does a logical OR.
 */
public class OrTrigger extends Trigger {

    private final List<Trigger> triggers = new LinkedList<>();
    
    public OrTrigger(List<Trigger> triggers) throws Exception {
        this.triggers.addAll(triggers);
    }

    private boolean checkSubTriggers(List<TriggerStatus> subStatuses) throws Exception {
        for (TriggerStatus status : subStatuses) {
            if (status.isReady()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TriggerStatus getTriggerStatus(StateDatabaseConnection connection, ScheduledTime now, ScheduledTime scheduledTime) throws Exception {
        final List<TriggerStatus> subStatuses = new ArrayList<>();
        for (Trigger trigger : triggers) {
            subStatuses.add(trigger.getTriggerStatus(connection, now, scheduledTime));
        }
        boolean ready = this.checkSubTriggers(subStatuses);
        return makeTriggerStatus(ready, humanReadableDescription(ready), subStatuses);
    }
    
    private String humanReadableDescription(boolean ready) {
        if (ready) {
            return "One or more nested triggers are ready";
        } else {
            return "None of the nested triggers are ready";
        }
    }

    public List<Trigger> getTriggers() {
        return triggers;
    }

}
