/*
 * MIT License
 *
 * Copyright (c) 2020 Dataline
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.dataline.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.MoreExecutors;
import io.dataline.config.JobOutput;
import io.dataline.scheduler.persistence.SchedulerPersistence;
import io.dataline.workers.JobStatus;
import io.dataline.workers.OutputAndStatus;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

public class JobSubmitterTest {

  public static final OutputAndStatus<JobOutput> SUCCESS_OUTPUT = new OutputAndStatus<>(JobStatus.SUCCESSFUL, new JobOutput());
  public static final OutputAndStatus<JobOutput> FAILED_OUTPUT = new OutputAndStatus<>(JobStatus.FAILED);

  private SchedulerPersistence persistence;
  private WorkerRunFactory workerRunFactory;
  private WorkerRun workerRun;

  private JobSubmitter jobSubmitter;

  @BeforeEach
  public void setup() throws IOException {
    Job job = mock(Job.class);
    when(job.getId()).thenReturn(1L);
    persistence = mock(SchedulerPersistence.class);
    when(persistence.getOldestPendingJob()).thenReturn(Optional.of(job));

    workerRun = mock(WorkerRun.class);
    workerRunFactory = mock(WorkerRunFactory.class);
    when(workerRunFactory.create(job)).thenReturn(workerRun);

    jobSubmitter = new JobSubmitter(
        MoreExecutors.newDirectExecutorService(),
        persistence,
        workerRunFactory);
  }

  @Test
  public void testPersistenceNoJob() throws Exception {
    doReturn(Optional.empty()).when(persistence).getOldestPendingJob();

    jobSubmitter.run();

    verifyNoInteractions(workerRunFactory);
  }

  @Test
  public void testSuccess() throws Exception {
    doReturn(SUCCESS_OUTPUT).when(workerRun).call();

    jobSubmitter.run();

    InOrder inOrder = inOrder(persistence);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.RUNNING);
    inOrder.verify(persistence).writeOutput(1L, new JobOutput());
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.COMPLETED);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testFailure() throws Exception {
    doReturn(FAILED_OUTPUT).when(workerRun).call();

    jobSubmitter.run();

    InOrder inOrder = inOrder(persistence);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.RUNNING);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.FAILED);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testException() throws Exception {
    doThrow(new RuntimeException()).when(workerRun).call();

    jobSubmitter.run();

    InOrder inOrder = inOrder(persistence);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.RUNNING);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.FAILED);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testPersistenceExceptionStart() throws Exception {
    doThrow(new RuntimeException()).when(persistence).updateStatus(anyLong(), any());

    jobSubmitter.run();

    InOrder inOrder = inOrder(persistence);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.FAILED);
    inOrder.verifyNoMoreInteractions();
  }

  @Test
  public void testPersistenceExceptionOutput() throws Exception {
    doReturn(SUCCESS_OUTPUT).when(workerRun).call();
    doThrow(new RuntimeException()).when(persistence).writeOutput(anyLong(), any());

    jobSubmitter.run();

    InOrder inOrder = inOrder(persistence);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.RUNNING);
    inOrder.verify(persistence).updateStatus(1L, io.dataline.scheduler.JobStatus.FAILED);
    inOrder.verifyNoMoreInteractions();
  }

}