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

import java.nio.ByteBuffer;

// key/value could be null
public class KeyValueRecord {

  private final long taskAttemptId;
  private final ByteBuffer keyBuffer;
  private final ByteBuffer valueBuffer;

  public KeyValueRecord(long taskAttemptId, ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
    this.taskAttemptId = taskAttemptId;
    this.keyBuffer = keyBuffer;
    this.valueBuffer = valueBuffer;
  }

  public long getTaskAttemptId() {
    return taskAttemptId;
  }

  public ByteBuffer getKeyBuffer() {
    return keyBuffer;
  }

  public ByteBuffer getValueBuffer() {
    return valueBuffer;
  }

  @Override
  public String toString() {
    return "KeyValueRecord{" +
        "taskAttemptId=" + taskAttemptId +
        ", keyBuffer=" + keyBuffer +
        ", valueBuffer=" + valueBuffer +
        '}';
  }
}
