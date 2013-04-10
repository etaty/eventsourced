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

case class ReplayParams(processorId: Int, fromSequenceNr: Long, withSnapshot: Boolean, p: SnapshotMetadata => Boolean) {
  require(!(withSnapshot && fromSequenceNr > 0), "cannot replay with snapshot when fromSequenceNr > 0")
}

object ReplayParams {
  def apply(processorId: Int, fromSequenceNr: Long = 0L): ReplayParams =
    ReplayParams(processorId, fromSequenceNr, false, _ => false)

  def apply(processorId: Int, p: SnapshotMetadata => Boolean): ReplayParams =
    ReplayParams(processorId, 0L, true, p)

  def apply(processorId: Int, withSnapshot: Boolean): ReplayParams =
    ReplayParams(processorId, 0L, withSnapshot, _=> withSnapshot)

  def apply(processorId: Int): ReplayParams =
    apply(processorId, false)
}
