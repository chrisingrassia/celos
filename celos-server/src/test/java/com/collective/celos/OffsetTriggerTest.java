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
package com.collective.celos;

import com.collective.celos.database.StateDatabaseConnection;
import com.collective.celos.trigger.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class OffsetTriggerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private StateDatabaseConnection connection = mock(StateDatabaseConnection.class);
    private ScheduledTime now = new ScheduledTime("2014-01-01T05:00:00Z");

    @Before
    public void prepare() throws IOException {
        String root = tempFolder.getRoot().getPath();

        File triggerFile = new File(root, "2013-11-22/1500/_READY");
        triggerFile.getParentFile().mkdirs();
        triggerFile.createNewFile();

    }

    @Test
    public void testTriggerAddsSomething() throws Exception {
        Trigger dependent = new HDFSCheckTrigger(tempFolder.getRoot().getPath() + "/${year}-${month}-${day}/${hour}00/_READY",  "file:///");

        Trigger offsetTriggerPlus = new OffsetTrigger(60*60, dependent);
        assertTrue(offsetTriggerPlus.isDataAvailable(connection, ScheduledTime.now(), new ScheduledTime("2013-11-22T14:00Z")));

    }

    @Test
    public void testTriggerDoesNothing() throws Exception {
        Trigger dependent = new HDFSCheckTrigger(tempFolder.getRoot().getPath() + "/${year}-${month}-${day}/${hour}00/_READY",  "file:///");

        Trigger offsetTriggerPlus = new OffsetTrigger(0, dependent);
        assertTrue(offsetTriggerPlus.isDataAvailable(connection, ScheduledTime.now(), new ScheduledTime("2013-11-22T15:00Z")));

    }

    @Test
    public void testTriggerSubsSomething() throws Exception {
        Trigger dependent = new HDFSCheckTrigger(tempFolder.getRoot().getPath() + "/${year}-${month}-${day}/${hour}00/_READY",  "file:///");

        Trigger offsetTriggerMinus = new OffsetTrigger(-60*60, dependent);
        assertTrue(offsetTriggerMinus.isDataAvailable(connection, ScheduledTime.now(), new ScheduledTime("2013-11-22T16:00Z")));

    }

    @Test
    public void descriptionNotReady() throws Exception {
        Trigger dependent = new HDFSCheckTrigger(tempFolder.getRoot().getPath() + "/${year}-${month}-${day}/${hour}00/_READY",  "file:///");

        Trigger offsetTriggerPlus = new OffsetTrigger(60*60, dependent);
        final TriggerStatus triggerStatus = offsetTriggerPlus.getTriggerStatus(connection, now.plusHours(2), now);
        final String description = triggerStatus.getDescription();
        Assert.assertFalse(triggerStatus.isReady());
        Assert.assertEquals("Nested trigger offset by 3600 seconds not ready", description);
    }


}
