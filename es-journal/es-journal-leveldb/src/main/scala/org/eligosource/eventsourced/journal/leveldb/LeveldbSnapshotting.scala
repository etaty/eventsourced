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
package org.eligosource.eventsourced.journal.leveldb

import java.io._

import scala.collection.SortedSet
import scala.concurrent._

import akka.actor._
import akka.pattern.ask
import akka.util._

import org.eligosource.eventsourced.core._
import org.eligosource.eventsourced.journal.common.SynchronousWriteReplaySupport

private [leveldb] trait LeveldbSnapshotting { this: SynchronousWriteReplaySupport =>
  private val FilenamePattern = """^snapshot-(\d+)-(\d+)-(\d+)""".r
  private var snapshotMetadata = Map.empty[Int, SortedSet[SnapshotMetadata]]

  implicit val snapshotMetadataOrdering = new Ordering[SnapshotMetadata] {
    def compare(x: SnapshotMetadata, y: SnapshotMetadata) =
      if (x.processorId == y.processorId) math.signum(x.sequenceNr - y.sequenceNr).toInt
      else x.processorId - y.processorId
  }

  lazy val snapshotDir: File =
    if (props.snapshotDir.isAbsolute) props.snapshotDir
    else new File(props.dir, props.snapshotDir.getPath)

  def snapshotFile(metadata: SnapshotMetadata): File =
    new File(snapshotDir, s"snapshot-${metadata.processorId}-${metadata.sequenceNr}-${metadata.timestamp}")

  def props: LeveldbJournalProps

  override def loadSnapshot(processorId: Int, snapshotFilter: SnapshotMetadata => Boolean): Option[Snapshot] = {

    val selected = for {
      mds <- snapshotMetadata.get(processorId)
      md  <- mds.filter(snapshotFilter).lastOption
    } yield md

    selected.map { metadata =>
      val serializer = new LeveldbSnapshotSerializer(snapshotFile(metadata))
      Snapshot(metadata.processorId, metadata.sequenceNr, metadata.timestamp, serializer.read)
    }
  }

  override def saveSnapshot(snapshot: Snapshot): Future[SnapshotSaved] = {
    val snapshotter = context.actorOf(Props(new LeveldbSnapshotter)
      .withDispatcher("eventsourced.journal.snapshot-dispatcher"))
    snapshotter.ask(snapshot)(Timeout(props.snapshotSaveTimeout)).mapTo[SnapshotSaved]
  }

  override def snapshotSaved(metadata: SnapshotMetadata) {
    snapshotMetadata.get(metadata.processorId) match {
      case None      => snapshotMetadata = snapshotMetadata + (metadata.processorId -> SortedSet(metadata))
      case Some(mds) => snapshotMetadata = snapshotMetadata + (metadata.processorId -> (mds + metadata))
    }
  }

  def initSnapshotting() {
    if (!snapshotDir.exists) snapshotDir.mkdirs()

    val metadata = snapshotDir.listFiles.map(_.getName).collect {
      case FilenamePattern(pid, snr, tms) => SnapshotSaved(pid.toInt, snr.toLong, tms.toLong)
    }

    snapshotMetadata = SortedSet.empty[SnapshotMetadata] ++ metadata groupBy(_.processorId)
  }

  private class LeveldbSnapshotter extends Actor {
    def receive = {
      case s: Snapshot => {
        val serializer = new LeveldbSnapshotSerializer(snapshotFile(s))
        serializer.write(s.state)
        sender ! SnapshotSaved(s.processorId, s.sequenceNr, s.timestamp)
        context.stop(self)
      }
    }
  }
}

private [leveldb] class LeveldbSnapshotSerializer(file: File) {
  def write(obj: Any) {
    val fos = new FileOutputStream(file)
    val oos = new ObjectOutputStream(fos)
    oos.writeObject(obj)
    oos.close()
  }

  def read: Any = {
    val fis = new FileInputStream(file)
    val ois = new ClassLoaderObjectInputStream(getClass.getClassLoader, fis)
    val obj = ois.readObject()
    ois.close()
    obj
  }
}