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
package org.eligosource.eventsourced.example

import scala.concurrent.duration._
import scala.util._

import akka.actor._
import akka.pattern.ask
import akka.util._

import org.eligosource.eventsourced.core._
import org.eligosource.eventsourced.journal.inmem._

object SnapshotExample extends App {
  implicit val system = ActorSystem("example")
  implicit val timeout = Timeout(5 seconds)

  val journal: ActorRef = Journal(InmemJournalProps())
  val extension = EventsourcingExtension(system, journal)

  case class Increment(by: Int)

  class Processor extends Actor { this: Receiver =>
    var counter = 0

    def receive = {
      case Increment(by) => {
        counter += by
        println(s"incremented counter by ${by} to ${counter} (snr = ${sequenceNr})")
      }
      case sr @ SnapshotRequest(pid, snr, _) => {
        sr.process(counter)
        println(s"processed snapshot request for ctr = ${counter} (snr = ${snr})")

      }
      case so @ SnapshotOffer(Snapshot(_, snr, time, ctr: Int)) => {
        counter = ctr
        println(s"accepted snapshot offer for ctr = ${counter} (snr = ${snr} time = ${time}})")
      }
    }
  }

  import system.dispatcher
  import extension._

  def setup() {
    println("--- setup ---")
    processorOf(Props(new Processor with Receiver with Eventsourced { val id = 1 } ))
  }

  setup()
  processors(1) ! Message(Increment(1))
  processors(1) ! Message(Increment(2))
  processors(1) ? Snapshot onComplete {
    case Success(SnapshotSaved(pid, snr, time)) => println(s"snapshotting succeeded: pid = ${pid} snr = ${snr} time = ${time}")
    case Failure(e)                             => println("snapshotting failed: " + e)
  }
  processors(1) ! Message(Increment(3))
  Thread.sleep(500)

  setup()
  extension.recover(replayParams.allWithSnapshot)
  processors(1) ! Message(Increment(1))
  extension.snapshot(Set(1)) onComplete {
    case Success(_) => println("snapshotting succeeded")
    case Failure(e) => println("snapshotting failed: " + e)
  }
  processors(1) ! Message(Increment(2))
  Thread.sleep(500)

  setup()
  extension.recover(replayParams.allWithSnapshot)
  Thread.sleep(500)

  setup()
  extension.recover(replayParams.allWithSnapshot(_.sequenceNr < 4L))
  Thread.sleep(500)

  setup()
  extension.recover(replayParams.allFromScratch)
  Thread.sleep(500)

  system.shutdown()
}
