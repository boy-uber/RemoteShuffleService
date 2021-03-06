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

import com.uber.rss.common.AppTaskAttemptId;
import com.uber.rss.messages.ConnectUploadResponse;

import java.nio.ByteBuffer;

/***
 * Shuffle write client to upload data (records) to shuffle server.
 */
public interface RecordSyncWriteClient extends SingleServerWriteClient {

  String getHost();

  int getPort();

  String getUser();

  String getAppId();

  String getAppAttempt();

  ConnectUploadResponse connect();

  void startUpload(AppTaskAttemptId appTaskAttemptId, int numMaps, int numPartitions);

  void sendRecord(int partition, ByteBuffer key, ByteBuffer value);

  void finishUpload();

  long getShuffleWriteBytes();
}
