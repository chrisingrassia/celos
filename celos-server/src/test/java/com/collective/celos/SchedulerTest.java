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

import com.collective.celos.trigger.AlwaysTrigger;
import com.collective.celos.trigger.Trigger;
import com.collective.celos.trigger.TriggerStatus;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.*;

import static org.mockito.Matchers.anyListOf;
import static org.mockito.Mockito.*;

/**
 * TODO: test exception handling and logging
 * TODO: more fine-grained tests like multipleSlotsTest
 */
public class SchedulerTest {

    private static WorkflowInfo emptyWorkflowInfo = new WorkflowInfo(null, Collections.<WorkflowInfo.ContactsInfo>emptyList());

    /*
     * I prefer setting up test data in the test method, using local variables,
     * but a scheduler call needs a lot of data, and the objects model is quite
     * intertwined. So, rather than bloat each test method with a lot of object
     * creation, I've promoted common state to instance variables, initialized
     * in the setUp() method.
     * 
     * Some of these instance variables are mocks. Each interaction-based test
     * will have to configure the mocks as appropriate.
     */

    // Mocks
    private MemoryStateDatabase.MemoryStateDatabaseConnection connection;
    private MemoryStateDatabase.MemoryStateDatabaseConnection mockedConnection;
    private Trigger trigger;
    private Schedule schedule;
    private SchedulingStrategy schedulingStrategy;
    private ExternalService externalService;

    // Lots of tests need these objects, so create them once in the setUp
    private WorkflowID workflowId;
    private Workflow wf;
    private ScheduledTime scheduledTime;
    private Scheduler scheduler;
    private SlotID slotId;

    @Before
    public void setUp() throws Exception {
        // Mocks
        connection = new MemoryStateDatabase().new MemoryStateDatabaseConnection();
        mockedConnection = mock(MemoryStateDatabase.MemoryStateDatabaseConnection.class);
        trigger = mock(Trigger.class);
        schedule = mock(Schedule.class);
        schedulingStrategy = mock(SchedulingStrategy.class);
        externalService = mock(ExternalService.class);
        int maxRetryCount = 0;

        // Objects
        workflowId = new WorkflowID("workflow-id");
        wf = new Workflow(workflowId, schedule, schedulingStrategy, trigger,
                externalService, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);
        scheduledTime = new ScheduledTime("2013-11-26T15:00Z");
        slotId = new SlotID(workflowId, scheduledTime);

        // The object under test
        scheduler = new Scheduler(new WorkflowConfiguration(), 1);
    }

    @Test
    public void runExternalWorkflowsNoCandidates() throws Exception {
        List<SlotState> slotStates = new ArrayList<SlotState>();
        scheduler.runExternalWorkflows(wf, slotStates, mockedConnection);
        verifyNoMoreInteractions(mockedConnection);
    }

    @Test(expected = IllegalStateException.class)
    public void runExternalWorkflowsWaitingCandidate() throws Exception {
        runExternalWorkflowsWithInvalidCandidate(SlotState.Status.WAITING);
    }

    @Test(expected = IllegalStateException.class)
    public void runExternalWorkflowsRunningCandidate() throws Exception {
        runExternalWorkflowsWithInvalidCandidate(SlotState.Status.RUNNING);
    }

    @Test(expected = IllegalStateException.class)
    public void runExternalWorkflowsSuccessCandidate() throws Exception {
        runExternalWorkflowsWithInvalidCandidate(SlotState.Status.SUCCESS);
    }

    @Test(expected = IllegalStateException.class)
    public void runExternalWorkflowsFailureCandidate() throws Exception {
        runExternalWorkflowsWithInvalidCandidate(SlotState.Status.FAILURE);
    }

    @Test(expected = IllegalStateException.class)
    public void runExternalWorkflowsFailureAndReadyCandidates() throws Exception {
        runExternalWorkflowsWithInvalidCandidate(SlotState.Status.FAILURE,
                SlotState.Status.READY);
    }

    private void runExternalWorkflowsWithInvalidCandidate(SlotState.Status... statuses) throws Exception {
        List<SlotState> slotStates = candidate(statuses);
        scheduler.runExternalWorkflows(wf, slotStates, mockedConnection);
        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void runExternalWorkflowsReadyCandidate() throws Exception {
        List<SlotState> slotStates = candidate(SlotState.Status.READY);
        SlotState nextSlotState = slotStates.get(0).transitionToRunning("externalId");
        when(externalService.submit(slotId)).thenReturn("externalId");
        scheduler.runExternalWorkflows(wf, slotStates, mockedConnection);
        verify(mockedConnection).putSlotState(nextSlotState);
        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void runExternalWorkflowsCallsSchedulerCorrectly() throws Exception {
        List<SlotState> slotStates = candidate(SlotState.Status.READY);
        when(externalService.submit(slotId)).thenReturn("externalId");
        scheduler.runExternalWorkflows(wf, slotStates, mockedConnection);

        InOrder inOrder = inOrder(externalService);
        inOrder.verify(externalService).submit(slotStates.get(0).getSlotID());
        inOrder.verify(externalService).start(slotId, "externalId");
        verifyNoMoreInteractions(externalService);
    }

    @Test
    public void runExternalWorkflowsMultipleReadyCandidates() throws Exception {
        SlotState slotState1 = makeReadySlotStateForTime("2013-11-26T15:01Z");
        SlotState slotState2 = makeReadySlotStateForTime("2013-11-26T15:02Z");

        List<SlotState> slotStates = new ArrayList<SlotState>();
        slotStates.add(slotState1);
        slotStates.add(slotState2);

        stubAsSchedulingCandidates(slotStates);

        SlotState nextSlotState1 = slotState1.transitionToRunning("externalId1");
        SlotState nextSlotState2 = slotState2.transitionToRunning("externalId2");

        when(externalService.submit(slotState1.getSlotID())).thenReturn("externalId1");
        when(externalService.submit(slotState2.getSlotID())).thenReturn("externalId2");

        scheduler.runExternalWorkflows(wf, slotStates, mockedConnection);
        verify(mockedConnection).putSlotState(nextSlotState1);
        verify(mockedConnection).putSlotState(nextSlotState2);
        verifyNoMoreInteractions(mockedConnection);
    }

    private SlotState makeReadySlotStateForTime(String timeString) {
        ScheduledTime time = new ScheduledTime(timeString);
        SlotID slotId = new SlotID(workflowId, time);
        return new SlotState(slotId, SlotState.Status.READY);
    }

    private List<SlotState> candidate(SlotState.Status... statuses) {
        List<SlotState> result = new ArrayList<SlotState>();
        for (SlotState.Status status : statuses) {
            result.add(new SlotState(slotId, status));
        }
        stubAsSchedulingCandidates(result);
        return result;
    }

    private void stubAsSchedulingCandidates(List<SlotState> slotStates) {
        when(schedulingStrategy.getSchedulingCandidates(anyListOf(SlotState.class))).thenReturn(slotStates);
    }

    @Test
    public void updateSlotStateWaitingAvailable() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.WAITING);
        SlotState nextSlotState = new SlotState(slotId, SlotState.Status.READY);

        // The trigger should report the data as available
        ScheduledTime now = ScheduledTime.now();
        final TriggerStatus status = new TriggerStatus("", true, "", Collections.<TriggerStatus>emptyList());
        when(trigger.getTriggerStatus(mockedConnection, now, scheduledTime)).thenReturn(status);

        scheduler.updateSlotState(wf, slotState, now, mockedConnection);

        verify(mockedConnection).putSlotState(nextSlotState);
        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void updateSlotStateWaitingNotAvailable() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.WAITING);

        // The trigger should report the data as not available
        ScheduledTime now = ScheduledTime.now();
        final TriggerStatus status = new TriggerStatus("", false, "", Collections.<TriggerStatus>emptyList());
        when(trigger.getTriggerStatus(mockedConnection, now, scheduledTime)).thenReturn(status);

        scheduler.updateSlotState(wf, slotState, now, mockedConnection);

        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void updateSlotStateReady() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.READY);

        scheduler.updateSlotState(wf, slotState, ScheduledTime.now(), mockedConnection);

        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void updateSlotStateRunningExternalIsRunning() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.RUNNING);

        // The external service should report the status as success
        ExternalStatus running = new MockExternalService.MockExternalStatusRunning();
        when(externalService.getStatus(slotId, slotState.getExternalID())).thenReturn(running);

        scheduler.updateSlotState(wf, slotState, ScheduledTime.now(), mockedConnection);

        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void updateSlotStateRunningExternalIsSuccess() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.RUNNING);
        SlotState nextSlotState = new SlotState(slotId, SlotState.Status.SUCCESS);

        // The external service should report the status as success
        ExternalStatus success = new MockExternalService.MockExternalStatusSuccess();
        when(externalService.getStatus(slotId, slotState.getExternalID())).thenReturn(success);

        scheduler.updateSlotState(wf, slotState, ScheduledTime.now(), mockedConnection);

        verify(mockedConnection).putSlotState(nextSlotState);
        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void updateSlotStateRunningExternalIsFailure() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.RUNNING);
        SlotState nextSlotState = new SlotState(slotId, SlotState.Status.FAILURE);

        // The external service should report the status as failure
        ExternalStatus failure = new MockExternalService.MockExternalStatusFailure();
        when(externalService.getStatus(slotId, slotState.getExternalID())).thenReturn(failure);

        scheduler.updateSlotState(wf, slotState, ScheduledTime.now(), mockedConnection);

        verify(mockedConnection).putSlotState(nextSlotState);
        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void updateSlotStateSuccess() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.SUCCESS);

        scheduler.updateSlotState(wf, slotState, ScheduledTime.now(), mockedConnection);

        verifyNoMoreInteractions(mockedConnection);
    }

    @Test
    public void updateSlotStateFailure() throws Exception {

        SlotState slotState = new SlotState(slotId, SlotState.Status.FAILURE);

        scheduler.updateSlotState(wf, slotState, ScheduledTime.now(), mockedConnection);

        verifyNoMoreInteractions(mockedConnection);
    }

    @Test(expected=IllegalArgumentException.class)
    public void slidingWindowHoursPositive1() {
        new Scheduler(new WorkflowConfiguration(), 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void slidingWindowHoursPositive2() {
        new Scheduler(new WorkflowConfiguration(), -23);
    }

    @Test(expected=NullPointerException.class)
    public void configurationCannotBeNull() {
        new Scheduler(null, 1);
    }

    @Test
    public void slidingWindowSizeWorks() {
        ScheduledTime t = new ScheduledTime("2013-11-26T20:00Z");
        int hours = 5;
        Scheduler scheduler = new Scheduler(new WorkflowConfiguration(), hours);
        Assert.assertEquals(scheduler.getSlidingWindowStartTime(t), new ScheduledTime("2013-11-26T15:00Z"));
    }

    /**
     * Create a workflow with a hourly schedule and an always trigger.
     *
     * Step the workflow a single time.
     *
     * Ensure that all hourly slots have been changed to ready.
     */
    @Test
    public void updatesWaitingSlotsToReady() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        ExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        int slidingWindowHours = 24;
        DateTime current = DateTime.parse("2013-11-27T15:01Z");
        DateTime currentFullHour = Util.toFullHour(current);

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);
        sched.step(new ScheduledTime(current), connection);

        Assert.assertEquals(slidingWindowHours, connection.size());

        for (int i = 0; i < slidingWindowHours; i++) {
            SlotID id = new SlotID(wfID1, new ScheduledTime(currentFullHour.minusHours(i)));
            SlotState state = connection.getSlotState(id);
            if (state == null) {
                throw new AssertionError("Slot " + id + " not found.");
            }
            Assert.assertEquals(SlotState.Status.READY, state.getStatus());
        }

    }

    /**
     * Make sure that scheduler doesn't care about very old slot.
     *
     * Mark the slot for rerun.
     *
     * Ensure the scheduler now cares about it.
     */
    @Test
    public void rerunTest() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeTrivialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        ExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        int slidingWindowHours = 24;

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);
        ScheduledTime now = new ScheduledTime("2013-11-27T15:01Z");
        sched.step(now, connection);
        // very old slot doesn't exist in DB because it's outside of sliding window
        SlotID id = new SlotID(wfID1, new ScheduledTime("2000-11-27T15:01Z"));
        Assert.assertEquals(null, connection.getSlotState(id));
        // mark the slot for rerun and verify scheduler cares about it
        connection.markSlotForRerun(id, now);
        sched.step(now, connection);
        Assert.assertEquals(SlotState.Status.READY, connection.getSlotState(id).getStatus());
        sched.step(now, connection);
        Assert.assertEquals(SlotState.Status.RUNNING, connection.getSlotState(id).getStatus());
    }

    /**
     * Create a workflow with a hourly schedule and a never trigger.
     *
     * Step the workflow a single time.
     *
     * Ensure that no hourly slots have been changed to ready, and in fact,
     * that no slots have been updated in the database.
     */
    @Test
    public void doesNotUpdateWaitingSlotsToReadyWhenNoDataAvailability() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = new NeverTrigger();
        ExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        MemoryStateDatabase db = new MemoryStateDatabase();

        int slidingWindowHours = 24;
        DateTime current = DateTime.parse("2013-11-27T15:01Z");

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);
        sched.step(new ScheduledTime(current), mockedConnection);

        Assert.assertEquals(0, connection.size());
    }

    @Test
    public void updatesWaitingSlotsToWaitTimeout() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = new NeverTrigger();
        ExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        int waitTimeoutSeconds = 20;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, waitTimeoutSeconds, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        SlotID id1 = new SlotID(wfID1, new ScheduledTime("2013-11-27T22:00:00Z"));
        SlotState slot1 = new SlotState(id1, SlotState.Status.WAITING);
        connection.putSlotState(slot1);

        int slidingWindowHours = 24;

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);

        sched.step(new ScheduledTime("2013-11-27T22:00:19Z"), connection);
        Assert.assertEquals(SlotState.Status.WAITING, connection.getSlotState(id1).getStatus());
        sched.step(new ScheduledTime("2013-11-27T22:00:20Z"), connection);
        Assert.assertEquals(SlotState.Status.WAITING, connection.getSlotState(id1).getStatus());
        sched.step(new ScheduledTime("2013-11-27T22:00:21Z"), connection);
        Assert.assertEquals(SlotState.Status.WAIT_TIMEOUT, connection.getSlotState(id1).getStatus());
    }

    /**
     * Creates running slots in memory database, with mock external service
     * that always says the external jobs are still running.
     *
     * Makes sure that the slots are still marked as running after step.
     */
    @Test
    public void leavesRunningSlotsAsIsIfStillRunning() throws Exception {
        runningSlotUtil(new MockExternalService.MockExternalStatusRunning(), SlotState.Status.RUNNING);
    }

    /**
     * Creates running slots in memory database, with mock external service
     * that always says the external jobs are successful.
     *
     * Makes sure that the slots are marked as successful after step.
     */
    @Test
    public void marksRunningSlotsAsSuccessfulIfExternalStatusIsSuccess() throws Exception {
        runningSlotUtil(new MockExternalService.MockExternalStatusSuccess(), SlotState.Status.SUCCESS);
    }

    /**
     * Creates running slots in memory database, with mock external service
     * that always says the external jobs are failed.
     *
     * Makes sure that the slots are marked as failed after step.
     */
    @Test
    public void marksRunningSlotsAsFailedIfExternalStatusIsFailure() throws Exception {
        runningSlotUtil(new MockExternalService.MockExternalStatusFailure(), SlotState.Status.FAILURE);
    }

    private void runningSlotUtil(ExternalStatus externalStatus, SlotState.Status expectedSlotStatus) throws Exception, AssertionError {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        ExternalService srv1 = new MockExternalService(externalStatus);
        int maxRetryCount = 0;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        int slidingWindowHours = 24;
        DateTime current = DateTime.parse("2013-11-27T15:01Z");
        DateTime currentFullHour = Util.toFullHour(current);

        for (int i = 0; i < slidingWindowHours; i++) {
            SlotID id = new SlotID(wfID1, new ScheduledTime(currentFullHour.minusHours(i)));
            SlotState state = new SlotState(id, SlotState.Status.READY).transitionToRunning("fake-external-ID");
            connection.putSlotState(state);
        }

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);
        sched.step(new ScheduledTime(current), connection);

        Assert.assertEquals(slidingWindowHours, connection.size());

        for (int i = 0; i < slidingWindowHours; i++) {
            SlotID id = new SlotID(wfID1, new ScheduledTime(currentFullHour.minusHours(i)));
            SlotState state = connection.getSlotState(id);
            if (state == null) {
                throw new AssertionError("Slot " + id + " not found.");
            }
            Assert.assertEquals(expectedSlotStatus, state.getStatus());
        }
    }

    /**
     * Creates ready slots in memory database.
     *
     * Uses trivial scheduling strategy so all ready slots will be used.
     *
     * Makes sure all are running after step, and have been submitted to external service.
     */
    @Test
    public void updatesReadySlotsToRunningAndSubmitsThemToExternalSystem() throws Exception, AssertionError {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeTrivialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        MockExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        int slidingWindowHours = 24;
        DateTime current = DateTime.parse("2013-11-27T15:01Z");
        DateTime currentFullHour = Util.toFullHour(current);

        for (int i = 0; i < slidingWindowHours; i++) {
            SlotID id = new SlotID(wfID1, new ScheduledTime(currentFullHour.minusHours(i)));
            SlotState state = new SlotState(id, SlotState.Status.READY);
            connection.putSlotState(state);
        }

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);
        sched.step(new ScheduledTime(current), connection);

        Assert.assertEquals(slidingWindowHours, connection.size());
        Assert.assertEquals(slidingWindowHours, srv1.getSlots2ExternalID().size());

        for (int i = 0; i < slidingWindowHours; i++) {
            ScheduledTime scheduledTime = new ScheduledTime(currentFullHour.minusHours(i));
            SlotID id = new SlotID(wfID1, scheduledTime);
            SlotState state = connection.getSlotState(id);
            if (state == null) {
                throw new AssertionError("Slot " + id + " not found.");
            }
            Assert.assertEquals(SlotState.Status.RUNNING, state.getStatus());
            String externalID = state.getExternalID();
            Assert.assertNotNull(externalID);
            Assert.assertEquals(externalID, srv1.getSlots2ExternalID().get(id));
        }
    }

    /**
     * Use a serial scheduling strategy.
     *
     * Create one waiting and two ready slots.
     *
     * Make sure that after a step:
     *
     * - the waiting slot is ready
     *
     * - the first ready slot is running
     *
     * - the second ready slot is still ready
     *
     * - the running slot's external ID matches the one handed out by the mock external service
     */
    @Test
    public void multipleSlotsTest() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        MockExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        MemoryStateDatabase db = new MemoryStateDatabase();

        SlotID id1 = new SlotID(wfID1, new ScheduledTime("2013-11-27T20:00Z"));
        SlotID id2 = new SlotID(wfID1, new ScheduledTime("2013-11-27T21:00Z"));
        SlotID id3 = new SlotID(wfID1, new ScheduledTime("2013-11-27T22:00Z"));

        SlotState slot1 = new SlotState(id1, SlotState.Status.WAITING);
        SlotState slot2 = new SlotState(id2, SlotState.Status.WAITING).transitionToReady();
        SlotState slot3 = new SlotState(id3, SlotState.Status.WAITING).transitionToReady();

        connection.putSlotState(slot1);
        connection.putSlotState(slot2);
        connection.putSlotState(slot3);

        int slidingWindowHours = 3;
        DateTime current = DateTime.parse("2013-11-27T22:01Z");

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);
        sched.step(new ScheduledTime(current), connection);

        SlotState slot1After = connection.getSlotState(id1);
        Assert.assertEquals(SlotState.Status.READY, slot1After.getStatus());

        SlotState slot2After = connection.getSlotState(id2);
        Assert.assertEquals(SlotState.Status.RUNNING, slot2After.getStatus());

        SlotState slot3After = connection.getSlotState(id3);
        Assert.assertEquals(SlotState.Status.READY, slot3After.getStatus());

        String externalID = slot2After.getExternalID();
        Assert.assertNotNull(externalID);
        Assert.assertEquals(externalID, srv1.getSlots2ExternalID().get(slot2.getSlotID()));
    }

    /**
     * Create a workflow with a start time 3 days in the past.
     *
     * Run scheduler for past 7 days.
     *
     * Ensure that only slots for the past three days have been created in the connection.
     */
    @Test
    public void workflowStartTimeTest() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        MockExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        DateTime currentDT = DateTime.now(DateTimeZone.UTC);
        ScheduledTime startTime = new ScheduledTime(currentDT.minusDays(3));

        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, startTime, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        Scheduler sched = new Scheduler(cfg, 7 * 24);
        sched.step(new ScheduledTime(currentDT), connection);

        Assert.assertEquals(3 * 24, connection.size());
    }

    /**
     * Make sure all slots have been processed if workflow start time is before sliding window start time.
     */
    @Test
    public void workflowStartTimeTest2() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        MockExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        DateTime currentDT = DateTime.now(DateTimeZone.UTC);
        ScheduledTime startTime = new ScheduledTime(currentDT.minusDays(10));

        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, startTime, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);
        
        Scheduler sched = new Scheduler(cfg, 7 * 24);
        sched.step(new ScheduledTime(currentDT), connection);

        Assert.assertEquals(7 * 24, connection.size());
    }

    /**
     * Make sure no slots have been processed if workflow start time is in the future.
     */
    @Test
    public void workflowStartTimeTest3() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        MockExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        DateTime currentDT = DateTime.now(DateTimeZone.UTC);
        ScheduledTime startTime = new ScheduledTime(currentDT.plusDays(10));

        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, startTime, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        MemoryStateDatabase db = new MemoryStateDatabase();

        Scheduler sched = new Scheduler(cfg, 7 * 24);
        sched.step(new ScheduledTime(currentDT), mockedConnection);

        Assert.assertEquals(0, connection.size());
    }

    /**
     * External service that fails a configurable number of times and then succeeds.
     */
    public static class RepeatedlyFailingExternalService implements ExternalService {

        private int failuresLeft;

        public RepeatedlyFailingExternalService(int failures) {
            this.failuresLeft = failures;
        }

        @Override
        public String submit(SlotID id) throws ExternalServiceException {
            return "fake-external-id";
        }

        @Override
        public void start(SlotID id, String externalID) throws ExternalServiceException {
            Assert.assertEquals("fake-external-id", externalID);
            failuresLeft--;
        }

        @Override
        public ExternalStatus getStatus(SlotID id, String externalWorkflowID) throws ExternalServiceException {
            if (failuresLeft < 0) {
                return new MockExternalService.MockExternalStatusSuccess();
            } else {
                return new MockExternalService.MockExternalStatusFailure();
            }
        }

        @Override
        public void kill(SlotID id, String externalID) throws ExternalServiceException {

        }

    }

    @Test
    public void testRepeatedlyFailingExternalService() throws ExternalServiceException {
        ExternalService srv = new RepeatedlyFailingExternalService(2);
        srv.start(slotId, "fake-external-id");
        Assert.assertFalse(srv.getStatus(slotId, "fake-external-id").isSuccess());
        srv.start(slotId, "fake-external-id");
        Assert.assertFalse(srv.getStatus(slotId, "fake-external-id").isSuccess());
        srv.start(slotId, "fake-external-id");
        Assert.assertTrue(srv.getStatus(slotId, "fake-external-id").isSuccess());
    }


    /**
     * Set up workflow with max retry count of 10.
     *
     * Use repeatedly failing external service that fails 2 times.
     *
     * Ensure that slot is rerun 2 times and then moves to success state.
     */
    @Test
    public void retryTest() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = new TrivialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        ExternalService srv1 = new RepeatedlyFailingExternalService(2);
        int maxRetryCount = 10;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        MemoryStateDatabase db = new MemoryStateDatabase();

        SlotID id1 = new SlotID(wfID1, new ScheduledTime("2013-11-27T20:00Z"));
        SlotState initial = new SlotState(id1, SlotState.Status.READY);
        SlotState running1 = new SlotState(id1, SlotState.Status.RUNNING, "fake-external-id", 0);
        SlotState retry1 = new SlotState(id1, SlotState.Status.WAITING, null, 1);
        SlotState ready1 = new SlotState(id1, SlotState.Status.READY, null, 1);
        SlotState running2 = new SlotState(id1, SlotState.Status.RUNNING, "fake-external-id", 1);
        SlotState retry2 = new SlotState(id1, SlotState.Status.WAITING, null, 2);
        SlotState ready2 = new SlotState(id1, SlotState.Status.READY, null, 2);
        SlotState running3 = new SlotState(id1, SlotState.Status.RUNNING, "fake-external-id", 2);
        SlotState success = new SlotState(id1, SlotState.Status.SUCCESS, "fake-external-id", 2);

        connection.putSlotState(initial);

        Scheduler sch = new Scheduler(cfg, 1);
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(running1, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(retry1, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(ready1, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(running2, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(retry2, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(ready2, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(running3, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(success, connection.getSlotState(id1));
    }

    /**
     * Set up workflow with max retry count of 2.
     *
     * Use repeatedly failing external service that fails 3 times.
     *
     * Ensure that slot is rerun 2 (max retry count) times and then moves to failure state.
     */
    @Test
    public void retryTestWithTooSmallMaxRetryCount() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = new TrivialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        ExternalService srv1 = new RepeatedlyFailingExternalService(3);
        int maxRetryCount = 2;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);

        MemoryStateDatabase db = new MemoryStateDatabase();

        SlotID id1 = new SlotID(wfID1, new ScheduledTime("2013-11-27T20:00Z"));
        SlotState initial = new SlotState(id1, SlotState.Status.READY);
        SlotState running1 = new SlotState(id1, SlotState.Status.RUNNING, "fake-external-id", 0);
        SlotState retry1 = new SlotState(id1, SlotState.Status.WAITING, null, 1);
        SlotState ready1 = new SlotState(id1, SlotState.Status.READY, null, 1);
        SlotState running2 = new SlotState(id1, SlotState.Status.RUNNING, "fake-external-id", 1);
        SlotState retry2 = new SlotState(id1, SlotState.Status.WAITING, null, 2);
        SlotState ready2 = new SlotState(id1, SlotState.Status.READY, null, 2);
        SlotState running3 = new SlotState(id1, SlotState.Status.RUNNING, "fake-external-id", 2);
        SlotState failure = new SlotState(id1, SlotState.Status.FAILURE, "fake-external-id", 2);

        connection.putSlotState(initial);

        Scheduler sch = new Scheduler(cfg, 1);
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(running1, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(retry1, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(ready1, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(running2, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(retry2, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(ready2, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(running3, connection.getSlotState(id1));
        sch.step(new ScheduledTime("2013-11-27T20:01Z"), connection);
        Assert.assertEquals(failure, connection.getSlotState(id1));
    }

    /**
     * Set up two identical workflows but tell scheduler to only schedule the first one.
     *
     * Check that second workflow's slot is not touched and stays in WAITING.
     */
    @Test
    public void schedulingOnlySubsetOfWorkflowsWorks() throws Exception {
        WorkflowID wfID1 = new WorkflowID("wf1");
        WorkflowID wfID2 = new WorkflowID("wf2");
        Schedule sch1 = makeHourlySchedule();
        SchedulingStrategy str1 = makeSerialSchedulingStrategy();
        Trigger tr1 = makeAlwaysTrigger();
        MockExternalService srv1 = new MockExternalService(new MockExternalService.MockExternalStatusRunning());
        int maxRetryCount = 0;
        Workflow wf1 = new Workflow(wfID1, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);
        Workflow wf2 = new Workflow(wfID2, sch1, str1, tr1, srv1, maxRetryCount, Workflow.DEFAULT_START_TIME, Workflow.DEFAULT_WAIT_TIMEOUT_SECONDS, emptyWorkflowInfo);

        WorkflowConfiguration cfg = new WorkflowConfiguration();
        cfg.addWorkflow(wf1);
        cfg.addWorkflow(wf2);

        MemoryStateDatabase db = new MemoryStateDatabase();

        SlotID id1 = new SlotID(wfID1, new ScheduledTime("2013-11-27T20:00Z"));
        SlotID id2 = new SlotID(wfID2, new ScheduledTime("2013-11-27T20:00Z"));

        SlotState slot1 = new SlotState(id1, SlotState.Status.WAITING);
        SlotState slot2 = new SlotState(id2, SlotState.Status.WAITING);

        connection.putSlotState(slot1);
        connection.putSlotState(slot2);

        Set<WorkflowID> subset = new HashSet<>();
        subset.add(wfID1);

        int slidingWindowHours = 3;
        DateTime current = DateTime.parse("2013-11-27T22:01Z");

        Scheduler sched = new Scheduler(cfg, slidingWindowHours);
        sched.step(new ScheduledTime(current), subset, connection);

        SlotState slot1After = connection.getSlotState(id1);
        Assert.assertEquals(SlotState.Status.READY, slot1After.getStatus());

        SlotState slot2After = connection.getSlotState(id2);
        Assert.assertEquals(SlotState.Status.WAITING, slot2After.getStatus());
    }

    @Test
    public void testTimeoutCalculation() {
        Assert.assertTrue(Scheduler.isSlotTimedOut(new ScheduledTime("2015-03-05T00:00:00Z"), new ScheduledTime("2015-03-05T00:00:21Z"), 20));
        Assert.assertFalse(Scheduler.isSlotTimedOut(new ScheduledTime("2015-03-05T00:00:00Z"), new ScheduledTime("2015-03-05T00:00:20Z"), 20));
        Assert.assertFalse(Scheduler.isSlotTimedOut(new ScheduledTime("2015-03-05T00:00:00Z"), new ScheduledTime("2014-03-05T00:00:20Z"), 20));
        Assert.assertTrue(Scheduler.isSlotTimedOut(new ScheduledTime("2015-03-05T00:00:00Z"), new ScheduledTime("2016-03-05T00:00:21Z"), 20));
    }

    @Test
    public void testGetSlotStates() throws Exception {

        WorkflowID id = wf.getID();

        ScheduledTime time1_noSlot = new ScheduledTime("2015-03-05T01:00:00Z");
        ScheduledTime time2_slot = new ScheduledTime("2015-03-05T02:00:00Z");
        ScheduledTime time3_slot = new ScheduledTime("2015-03-05T03:00:00Z");
        ScheduledTime time4_noSlot = new ScheduledTime("2015-03-05T04:00:00Z");
        ScheduledTime time5_slot = new ScheduledTime("2015-03-05T05:00:00Z");
        ScheduledTime time6_noSlot = new ScheduledTime("2015-03-05T06:00:00Z");
        ScheduledTime time7_noSlot = new ScheduledTime("2015-03-05T07:00:00Z");

        SortedSet<ScheduledTime> scheduledTimes = Sets.newTreeSet();
        scheduledTimes.add(time1_noSlot);
        scheduledTimes.add(time2_slot);
//        OMIT THOSE FOR TESTING
//        scheduledTimes.add(time3_slot);
//        scheduledTimes.add(time4_noSlot);
        scheduledTimes.add(time5_slot);
        scheduledTimes.add(time6_noSlot);

        Map<SlotID, SlotState> dbSlotStates1 = Maps.newHashMap();
        SlotID slotID1 = new SlotID(id, time2_slot);
        dbSlotStates1.put(slotID1, new SlotState(slotID1, SlotState.Status.SUCCESS));
        SlotID slotID2 = new SlotID(id, time3_slot);
        dbSlotStates1.put(slotID2,new SlotState(slotID2, SlotState.Status.SUCCESS));
        SlotID slotID3 = new SlotID(id, time5_slot);
        dbSlotStates1.put(slotID3, new SlotState(slotID3, SlotState.Status.SUCCESS));

        when(mockedConnection.getSlotStates(id, time1_noSlot, time7_noSlot)).thenReturn(dbSlotStates1);
        when(schedule.getScheduledTimes(scheduler, time1_noSlot, time7_noSlot)).thenReturn(scheduledTimes);

        List<SlotState> slotStates = scheduler.getSlotStates(wf, time1_noSlot, time7_noSlot, mockedConnection);
        Assert.assertEquals(slotStates.size(), 4);
        Assert.assertEquals(slotStates.get(0).getStatus(), SlotState.Status.WAITING);
        Assert.assertEquals(slotStates.get(0).getScheduledTime(), time1_noSlot);
        Assert.assertEquals(slotStates.get(1).getStatus(), SlotState.Status.SUCCESS);
        Assert.assertEquals(slotStates.get(1).getScheduledTime(), time2_slot);
        Assert.assertEquals(slotStates.get(2).getStatus(), SlotState.Status.SUCCESS);
        Assert.assertEquals(slotStates.get(2).getScheduledTime(), time5_slot);
        Assert.assertEquals(slotStates.get(3).getStatus(), SlotState.Status.WAITING);
        Assert.assertEquals(slotStates.get(3).getScheduledTime(), time6_noSlot);
    }

    @Test
    public void testGetSlotStatesIncludingMarkedForRerun() throws Exception {

        WorkflowID id = wf.getID();

        ScheduledTime time1_noSlot = new ScheduledTime("2015-03-05T01:00:00Z");
        ScheduledTime time2_slot = new ScheduledTime("2015-03-05T02:00:00Z");
        ScheduledTime time3_slot = new ScheduledTime("2015-03-05T03:00:00Z");
        ScheduledTime time4_noSlot = new ScheduledTime("2015-03-05T04:00:00Z");
        ScheduledTime time5_slot = new ScheduledTime("2015-03-05T05:00:00Z");
        ScheduledTime time6_noSlot = new ScheduledTime("2015-03-05T06:00:00Z");
        ScheduledTime time7_noSlot = new ScheduledTime("2015-03-05T07:00:00Z");

        ScheduledTime nowTimeForRerun = new ScheduledTime("2015-03-15T07:00:00Z");

        SortedSet<ScheduledTime> scheduledTimes = Sets.newTreeSet();
        scheduledTimes.add(time1_noSlot);
        scheduledTimes.add(time2_slot);
//        OMIT THOSE FOR TESTING
//        scheduledTimes.add(time3_slot);
//        scheduledTimes.add(time4_noSlot);
        scheduledTimes.add(time5_slot);
        scheduledTimes.add(time6_noSlot);

        Map<SlotID, SlotState> dbSlotStates1 = Maps.newHashMap();
        SlotID slotID1 = new SlotID(id, time2_slot);
        dbSlotStates1.put(slotID1, new SlotState(slotID1, SlotState.Status.SUCCESS));
        SlotID slotID2 = new SlotID(id, time3_slot);
        dbSlotStates1.put(slotID2,new SlotState(slotID2, SlotState.Status.SUCCESS));
        SlotID slotID3 = new SlotID(id, time5_slot);
        dbSlotStates1.put(slotID3,new SlotState(slotID3, SlotState.Status.SUCCESS));
        SortedSet<ScheduledTime> rerunTimes = Sets.newTreeSet();
        ScheduledTime timeRerun1 = new ScheduledTime("2015-03-02T07:00:00Z");
        ScheduledTime timeRerun2 = new ScheduledTime("2015-03-09T07:00:00Z");
        rerunTimes.add(timeRerun1);
        rerunTimes.add(timeRerun2);

        when(mockedConnection.getSlotStates(id, time1_noSlot, time7_noSlot)).thenReturn(dbSlotStates1);
        when(schedule.getScheduledTimes(scheduler, time1_noSlot, time7_noSlot)).thenReturn(scheduledTimes);
        when(mockedConnection.getTimesMarkedForRerun(workflowId, nowTimeForRerun)).thenReturn(rerunTimes);

        List<SlotState> slotStates = scheduler.getSlotStatesIncludingMarkedForRerun(wf, nowTimeForRerun, time1_noSlot, time7_noSlot, mockedConnection);
        Assert.assertEquals(slotStates.size(), 6);
        Assert.assertEquals(slotStates.get(0).getStatus(), SlotState.Status.WAITING);
        Assert.assertEquals(slotStates.get(0).getScheduledTime(), timeRerun1);
        Assert.assertEquals(slotStates.get(1).getStatus(), SlotState.Status.WAITING);
        Assert.assertEquals(slotStates.get(1).getScheduledTime(), time1_noSlot);
        Assert.assertEquals(slotStates.get(2).getStatus(), SlotState.Status.SUCCESS);
        Assert.assertEquals(slotStates.get(2).getScheduledTime(), time2_slot);
        Assert.assertEquals(slotStates.get(3).getStatus(), SlotState.Status.SUCCESS);
        Assert.assertEquals(slotStates.get(3).getScheduledTime(), time5_slot);
        Assert.assertEquals(slotStates.get(4).getStatus(), SlotState.Status.WAITING);
        Assert.assertEquals(slotStates.get(4).getScheduledTime(), time6_noSlot);
        Assert.assertEquals(slotStates.get(5).getStatus(), SlotState.Status.WAITING);
        Assert.assertEquals(slotStates.get(5).getScheduledTime(), timeRerun2);

    }

    private AlwaysTrigger makeAlwaysTrigger() {
        return new AlwaysTrigger();
    }

    private SerialSchedulingStrategy makeSerialSchedulingStrategy() {
        return new SerialSchedulingStrategy(1);
    }

    private TrivialSchedulingStrategy makeTrivialSchedulingStrategy() {
        return new TrivialSchedulingStrategy();
    }

    private HourlySchedule makeHourlySchedule() {
        return new HourlySchedule();
    }

}
