package com.eigengo.lift.exercise

import java.nio.ByteBuffer

import scodec.bits.{BitVector, ByteOrdering, ByteVector}

import scalaz.\/

/**
 * The multi packet message follows a simple structure
 *
 * {{{
 *    header: UInt16     = 0xcab1 // + 2 B
 *    count: Byte        = ...    // + 3 B
 *    timestamp: UInt32  = ...    // + 7 B
 *    ===
 *    size0: UInt16      = ...    // + 9 B
 *    sloc0: Byte        = ...    // + 10 B
 *    data0: Array[Byte] = ...
 *    size1: UInt16      = ...
 *    sloc1: Byte        = ...
 *    data1: Array[Byte] = ...
 *    ...
 *    sizen: UInt16      = ...
 *    slocn: Byte        = ...
 *    datan: Array[Byte] = ...
 * }}}
 */
object MultiPacketDecoder {
  private val header = 0xcab1.toShort

  def decodeShort(b0: Byte, b1: Byte): Int = {
    ByteVector(b0, b1).toInt(signed = false, ordering = ByteOrdering.BigEndian)
  }

  def decodeUInt32(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Long = {
    ByteVector(b0, b1, b2, b3).toLong(signed = false, ordering = ByteOrdering.BigEndian)
  }

  def decodeSensorDataSourceLocation(sloc: Byte): String \/ SensorDataSourceLocation = sloc match {
    case 0x01 ⇒ \/.right(SensorDataSourceLocationWrist)
    case 0x02 ⇒ \/.right(SensorDataSourceLocationWaist)
    case 0x03 ⇒ \/.right(SensorDataSourceLocationChest)
    case 0x04 ⇒ \/.right(SensorDataSourceLocationFoot)
    case 0x7f ⇒ \/.right(SensorDataSourceLocationAny)
    case x    ⇒ \/.left(s"Unknown sensor data source location $x")
  }

  def decode(input: ByteBuffer): String \/ MultiPacket = {
    if (input.limit() < 10) \/.left("No viable input: size < 10.")
    else {
      val inputHeader = input.getShort
      if (inputHeader != header) \/.left(s"Incorrect header. Expected $header, got $inputHeader.")
      else {
        val count = input.get()
        val timestamp = decodeUInt32(input.get(), input.get(), input.get(), input.get())
        if (count == 0) \/.left("No content.")
        else {
          val (h :: t) = (0 until count).toList.map { x ⇒
            if (input.position() + 3 >= input.limit()) \/.left(s"Incomplete or truncated input. (Header of packet $x.)")
            else {
              val size = decodeShort(input.get, input.get)
              if (input.position() + size >= input.limit()) \/.left(s"Incomplete or truncated input. ($size bytes payload of packet $x.)")
              else {
                val sloc = decodeSensorDataSourceLocation(input.get)
                val buf = input.slice().limit(size).asInstanceOf[ByteBuffer]
                input.position(input.position() + size)

                sloc.map(sloc ⇒ PacketWithLocation(sloc, BitVector(buf)))
              }
            }
          }

          t.foldLeft(h.map(MultiPacket.single(timestamp)))((r, b) ⇒ r.flatMap(mp ⇒ b.map(mp.withNewPacket)))
        }
      }
    }
  }

}
