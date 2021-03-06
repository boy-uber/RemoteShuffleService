/*
 * Copyright (c) 2020 Uber Technologies, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uber.rss.clients;

import com.uber.rss.common.AppShufflePartitionId;
import com.uber.rss.common.ServerReplicationGroup;
import com.uber.rss.exceptions.ExceptionWrapper;
import com.uber.rss.exceptions.RssInvalidStateException;
import com.uber.rss.exceptions.RssException;
import com.uber.rss.metrics.M3Stats;
import com.uber.rss.util.RetryUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This class read shuffle data from multiple shuffle servers for same shuffle partition.
 */
public class MultiServerSocketReadClient implements MultiServerReadClient {
  private static final Logger logger = LoggerFactory.getLogger(MultiServerSocketReadClient.class);

  private final int timeoutMillis;
  private final ClientRetryOptions clientRetryOptions;
  private final boolean compressed;
  private final int readQueueSize;
  private final String user;
  private final AppShufflePartitionId appShufflePartitionId;
  private final ReadClientDataOptions readClientDataOptions;
  private final boolean checkShuffleReplicaConsistency;

  private final List<ServerReplicationGroup> servers;

  private int nextClientIndex = 0;
  private long shuffleReadBytesOfFinishedClients = 0;

  private ReplicatedReadClient currentClient;

  public MultiServerSocketReadClient(Collection<ServerReplicationGroup> servers,
                                     int timeoutMillis,
                                     boolean compressed,
                                     int readQueueSize,
                                     String user,
                                     AppShufflePartitionId appShufflePartitionId,
                                     ReadClientDataOptions dataOptions,
                                     boolean checkShuffleReplicaConsistency) {
    this(servers,
        timeoutMillis,
        new ClientRetryOptions(dataOptions.getDataAvailablePollInterval(), timeoutMillis, serverDetail -> serverDetail),
        compressed,
        readQueueSize,
        user,
        appShufflePartitionId,
        dataOptions,
        checkShuffleReplicaConsistency);
  }

  public MultiServerSocketReadClient(Collection<ServerReplicationGroup> servers,
                                     int timeoutMillis,
                                     ClientRetryOptions retryOptions,
                                     boolean compressed,
                                     int readQueueSize,
                                     String user,
                                     AppShufflePartitionId appShufflePartitionId,
                                     ReadClientDataOptions dataOptions,
                                     boolean checkShuffleReplicaConsistency) {
    this.servers = new ArrayList<>(servers);
    this.timeoutMillis = timeoutMillis;
    this.clientRetryOptions = retryOptions;
    this.compressed = compressed;
    this.readQueueSize = readQueueSize;
    this.user = user;
    this.appShufflePartitionId = appShufflePartitionId;
    this.readClientDataOptions = dataOptions;
    this.checkShuffleReplicaConsistency = checkShuffleReplicaConsistency;

    if (servers.isEmpty()) {
      throw new RssException("No server provided");
    }
  }

  @Override
  public synchronized void connect() {
    connectAndInitializeClient();
  }

  @Override
  public synchronized void close() {
    closeClient(currentClient);
  }

  @Override
  public synchronized RecordKeyValuePair readRecord() {
    RecordKeyValuePair record = currentClient.readRecord();

    while (record == null) {
      shuffleReadBytesOfFinishedClients += currentClient.getShuffleReadBytes();
      currentClient.close();
      currentClient = null;
      if (nextClientIndex == servers.size()) {
        return null;
      } else if (nextClientIndex < servers.size()) {
        connectAndInitializeClient();
        record = currentClient.readRecord();
      } else {
        throw new RssInvalidStateException(String.format("Invalid nextClientIndex value: %s, max value: %s, %s", nextClientIndex, this.servers.size() - 1, this));
      }
    }

    return record;
  }

  @Override
  public synchronized long getShuffleReadBytes() {
    if (currentClient == null) {
      return shuffleReadBytesOfFinishedClients;
    } else {
      return shuffleReadBytesOfFinishedClients + currentClient.getShuffleReadBytes();
    }
  }

  @Override
  public String toString() {
    return "MultiServerSocketReadClient{" +
        "nextClientIndex=" + nextClientIndex +
        ", servers=" + servers +
        ", currentClient=" + currentClient +
        '}';
  }

  private void connectAndInitializeClient() {
    if (nextClientIndex > servers.size()) {
      throw new RssException(String.format("Invalid operation, next client index %s, total servers %s", nextClientIndex, servers.size()));
    }

    ServerReplicationGroup serverReplicationGroup = servers.get(nextClientIndex);
    logger.info(String.format("Fetching data from server: %s (%s out of %s), partition: %s", serverReplicationGroup, nextClientIndex + 1, servers.size(), appShufflePartitionId));

    ExceptionWrapper<Throwable> exceptionWrapper = new ExceptionWrapper<>();
    String failMsg = String.format("Failed to connect to server: %s, partition: %s", serverReplicationGroup, appShufflePartitionId);

    ReplicatedReadClient newClient = RetryUtils.retryUntilNotNull(clientRetryOptions.getRetryIntervalMillis(), clientRetryOptions.getRetryIntervalMillis()*10, clientRetryOptions.getRetryMaxMillis(), () -> {
      ReplicatedReadClient aClient = null;
      try {
        aClient = new ReplicatedReadClient(serverReplicationGroup,
            timeoutMillis,
            clientRetryOptions,
            compressed,
            readQueueSize,
            user,
            appShufflePartitionId,
            readClientDataOptions,
            checkShuffleReplicaConsistency);
        aClient.connect();
        return aClient;
      } catch (Throwable ex) {
        M3Stats.addException(ex, this.getClass().getSimpleName());
        logger.warn(failMsg, ex);
        exceptionWrapper.setException(ex);
        closeClient(aClient);
        return null;
      }
    });

    if (newClient == null) {
      if (exceptionWrapper.getException() == null) {
        throw new RssException(failMsg);
      } else if (exceptionWrapper.getException() instanceof RuntimeException) {
        throw (RuntimeException)exceptionWrapper.getException();
      } else {
        throw new RssException(failMsg, exceptionWrapper.getException());
      }
    }

    this.currentClient = newClient;
    nextClientIndex++;
  }

  private void closeClient(ReplicatedReadClient client) {
    try {
      if (client != null) {
        client.close();
      }
    } catch (Throwable ex) {
      logger.warn(String.format("Failed to close client %s", client));
    }
  }
}
