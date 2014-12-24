/*
 * Copyright (c) 2014 DataTorrent, Inc. ALL Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datatorrent.lib.io.fs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Queues;

import com.datatorrent.api.BaseOperator;
import com.datatorrent.api.Context;
import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.Operator.CheckpointListener;
import com.datatorrent.api.Operator.IdleTimeHandler;

import com.datatorrent.common.util.DTThrowable;
import com.datatorrent.common.util.NameableThreadFactory;

/**
 * This base operator queues input tuples for each window and asynchronously processes them after the window is committed.
 *
 * The operator holds all the tuple info in memory until the committed window and then calls the processCommittedData method
 * to give an opportunity to process tuple info from each committed window.
 *
 * This operator can be implemented to asynchronously read and process file data that is being written by current application.
 *
 * Use case examples: write to relational database, write to an external queue etc without blocking the dag i/o.
 *
 * This operator can be implemented in combination with {@link AbstractFSWriter} as streamlet to read and synchronize committed file data
 * to external systems.
 *
 * @param <INPUT>  input type
 * @param <QUEUETUPLE> tuple enqueued each window to be processed after window is committed
 */
public abstract class AbstractSynchronizer<INPUT, QUEUETUPLE> extends BaseOperator implements CheckpointListener, IdleTimeHandler
{
  private static final Logger logger = LoggerFactory.getLogger(AbstractSynchronizer.class);
  public transient DefaultInputPort<INPUT> input = new DefaultInputPort<INPUT>()
  {
    @Override
    public void process(INPUT input)
    {
      processTuple(input);
    }

  };
  protected transient ExecutorService executorService;
  protected long currentWindowId;
  protected transient int spinningTime;
  protected transient int operatorId;
  // this stores the mapping from the window to the list of file metas
  private Map<Long, List<QUEUETUPLE>> currentWindowTuples = Maps.newConcurrentMap();
  private Queue<Long> currentWindows = Queues.newLinkedBlockingQueue();
  private Queue<List<QUEUETUPLE>> committedTuples = Queues.newLinkedBlockingQueue();
  private transient volatile boolean execute;
  private transient volatile Throwable cause;

  @Override
  public void setup(Context.OperatorContext context)
  {
    operatorId = context.getId();
    spinningTime = context.getValue(Context.OperatorContext.SPIN_MILLIS);
    execute = true;
    executorService = Executors.newSingleThreadExecutor(new NameableThreadFactory("Synchronizer"));
    executorService.submit(processFiles());
  }

  @Override
  public void beginWindow(long windowId)
  {
    currentWindowId = windowId;
    currentWindowTuples.put(currentWindowId, new ArrayList<QUEUETUPLE>());
    currentWindows.add(windowId);
  }

  @Override
  public void handleIdleTime()
  {
    if (execute) {
      try {
        Thread.sleep(spinningTime);
      }
      catch (InterruptedException ie) {
        throw new RuntimeException(ie);
      }
    }
    else {
      logger.error("Exception: ", cause);
      DTThrowable.rethrow(cause);
    }

  }

  @Override
  public void checkpointed(long l)
  {
  }

  @Override
  public void committed(long l)
  {
    logger.debug(" current committed window {}", l);
    if (currentWindows.isEmpty()) {
      return;
    }
    long processedWindowId = currentWindows.peek();
    while (processedWindowId <= l) {
      List<QUEUETUPLE> outputDataList = currentWindowTuples.get(processedWindowId);
      if (outputDataList != null && !outputDataList.isEmpty()) {
        committedTuples.add(outputDataList);
      }
      currentWindows.remove();
      currentWindowTuples.remove(processedWindowId);
      if (currentWindows.isEmpty()) {
        return;
      }
      processedWindowId = currentWindows.peek();
    }
  }

  @Override
  public void teardown()
  {
    execute = false;
    executorService.shutdownNow();
  }

  private Runnable processFiles()
  {
    return new Runnable()
    {
      @Override
      public void run()
      {
        try {
          while (execute) {
            while (committedTuples.isEmpty()) {
              Thread.sleep(spinningTime);
            }
            List<QUEUETUPLE> outputList = committedTuples.peek();
            for (QUEUETUPLE output : outputList) {
              processCommittedData(output);
            }
            committedTuples.remove();
          }
        }
        catch (Throwable e) {
          cause = e;
          execute = false;
        }
      }
    };
  }

  /**
   * The implementation class should call this method to enqueue output once input is converted to queue input.
   *
   * The queueTuple is processed once the window in which queueTuple is enqueued is committed.
   *
   * @param queueTuple
   */
  protected void enqueueForProcessing(QUEUETUPLE queueTuple)
  {
    currentWindowTuples.get(currentWindowId).add(queueTuple);
  }

  /**
   * Process input tuple
   *
   * @param input
   */
  abstract protected void processTuple(INPUT input);

  /**
   * This method is called once the window in which queueTuple was created is committed.
   * Implement this method to define the functionality to synchronize data.
   *
   * @param queueInput
   */
  protected abstract void processCommittedData(QUEUETUPLE queueInput);
}