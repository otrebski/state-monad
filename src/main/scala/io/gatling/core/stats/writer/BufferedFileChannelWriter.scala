/*
 * Copyright 2011-2019 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.core.stats.writer

import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.charset.{CharsetEncoder, StandardCharsets}
import java.nio.file.{FileSystems, Path}
import java.nio.{ByteBuffer, CharBuffer}

import io.gatling.core.util.Longs

object BufferedFileChannelWriter {

  def apply(runId: String): BufferedFileChannelWriter = {
    val encoder: CharsetEncoder = StandardCharsets.US_ASCII.newEncoder()
    //    Charset.forName("US_ASCII").newEncoder()
    //    new File(s"out/$runId").mkdirs()
    val simulationLog: Path = FileSystems.getDefault.getPath("out", runId, "simulation.log")
    val channel = new RandomAccessFile(simulationLog.toFile, "rw").getChannel
    val bb = ByteBuffer.allocate(100 * 1024) //TODO increase
    new BufferedFileChannelWriter(channel, encoder, bb)
  }

  private val SIZE_TABLE = Array(9L,
    99L,
    999L,
    9999L,
    99999L,
    999999L,
    9999999L,
    99999999L,
    999999999L,
    9999999999L,
    99999999999L,
    999999999999L,
    9999999999999L,
    99999999999999L,
    999999999999999L,
    9999999999999999L,
    99999999999999999L,
    999999999999999999L,
    Long.MaxValue)

  private val index: Array[(Long, Int)] = SIZE_TABLE.zipWithIndex

  def longSize(long: Long): Int = {
    index.find(x => x._1 > long).get._2 + 1
  }
}

final class BufferedFileChannelWriter(channel: FileChannel, encoder: CharsetEncoder, bb: ByteBuffer)
  extends AutoCloseable {

  def startSimulation(clazz: String, simulationName: String, start: Long): Unit = {
    writeString("RUN")
    writeSeparator()
    writeString(clazz)
    writeSeparator()
    writeString(simulationName)
    writeSeparator()
    writePositiveLong(start)
    writeSeparator()
    writeString(" ")
    writeSeparator()
    writeString("3.0.3\n")
  }
  def startUser(userId: String, scenario: String, sessionStart: Long, now: Long): Unit = {
    writeUser(userId, scenario, sessionStart, now, "START")
  }

  private def writeUser(userId: String,
                        scenario: String,
                        sessionStart: Long,
                        now: Long,
                        event: String): Unit = {
    writeString("USER")
    writeSeparator()
    writeString(scenario)
    writeSeparator()
    writeString(userId)
    writeSeparator()
    writeString(event)
    writeSeparator()
    writePositiveLong(sessionStart)
    writeSeparator()
    writePositiveLong(now)
    writeString("\n")
  }
  def endUser(userId: String, scenario: String, sessionStart: Long, now: Long): Unit = {
    writeUser(userId, scenario, sessionStart, now, "END")
  }

  def response(userId: String, name: String, start: Long, end: Long, ok: Boolean = true): Unit = {
    writeString("REQUEST")
    writeSeparator()
    writeString(userId)
    writeSeparator()
    //ignore group
    writeSeparator()
    writeString(name)
    writeSeparator()
    writePositiveLong(start)
    writeSeparator()
    writePositiveLong(end)
    writeSeparator()
    writeString(if (ok) "OK" else "KO")
    writeSeparator()
    writeString(" ")
    writeString("\n")
  }

  def flush(): Unit = {
    bb.flip()
    while (bb.hasRemaining) {
      channel.write(bb)
    }
    bb.clear()
  }

  private def ensureCapacity(i: Int): Unit =
    if (bb.remaining < i) {
      flush()
    }

  private def writeString(string: String): Unit = {

    ensureCapacity(string.length * 4)

    val coderResult = encoder.encode(CharBuffer.wrap(string), bb, false)
    if (coderResult.isOverflow) {
      println("Buffer overflow, you shouldn't be logging that much data. Truncating.")
    }
  }
  private def writePositiveLong(l: Long): Unit = {
    val stringSize = Longs.positiveLongStringSize(l)
    ensureCapacity(stringSize)
    Longs.writePositiveLongString(l, stringSize, bb)
  }

  private def writeSeparator(): Unit = {
    writeString("\t")
  }

  override def close(): Unit = {
    flush()
    channel.force(true)
  }
}

