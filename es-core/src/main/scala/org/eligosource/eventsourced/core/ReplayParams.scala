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

sealed trait ReplayParams {
  def processorId: Int
  def fromSequenceNr: Long
  def toSequenceNr: Long
  def snapshot: Boolean
  def snapshotFilter: SnapshotMetadata => Boolean
}

object ReplayParams {
  def apply(processorId: Int, fromSequenceNr: Long = 0L): ReplayParams =
    StandardReplayParams(processorId, fromSequenceNr)

  def apply(processorId: Int, snapshotFilter: SnapshotMetadata => Boolean): ReplayParams =
    SnapshotReplayParams(processorId, snapshotFilter)

  def apply(processorId: Int, snapshot: Boolean): ReplayParams =
    SnapshotReplayParams(processorId)
}

case class StandardReplayParams(
  processorId: Int,
  fromSequenceNr: Long = 0L,
  toSequenceNr: Long = Long.MaxValue) extends ReplayParams {
  val snapshot = false
  def snapshotFilter = _ => false
}

case class SnapshotReplayParams(
  processorId: Int,
  snapshotFilter: SnapshotMetadata => Boolean = _ => true,
  toSequenceNr: Long = Long.MaxValue) extends ReplayParams {
  val fromSequenceNr = 0L
  val snapshot = true
}
