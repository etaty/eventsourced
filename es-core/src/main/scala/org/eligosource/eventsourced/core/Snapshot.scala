/*
 * Copyright 2012-2013 Eligotech BV.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eligosource.eventsourced.core

import akka.actor._

trait SnapshotMetadata {
  def processorId: Int
  def sequenceNr: Long
  def timestamp: Long
}

/** Processor state snapshot */
case class Snapshot(processorId: Int, sequenceNr: Long, timestamp: Long, state: Any) extends SnapshotMetadata {
  private [eventsourced] def withTimestamp: Snapshot = copy(timestamp = System.currentTimeMillis)
}

/** Offers a snapshot to a processor during replay */
case class SnapshotOffer(snapshot: Snapshot)

/** Reply to a `Snapshot` request */
case class SnapshotSaved(processorId: Int, sequenceNr: Long, timestamp: Long)

/** Requests a processor to provide its current state for storage */
case class SnapshotRequest(processorId: Int, sequenceNr: Long, requestor: ActorRef) {
  def process(state: Any)(implicit context: ActorContext) = {
    context.sender.tell(Journal.SaveSnapshot(Snapshot(processorId, sequenceNr, 0L, state)), requestor)
  }
}
