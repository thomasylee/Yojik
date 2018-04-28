package xyz.thomaslee.yojik.tls

import akka.actor.ActorRef
import akka.util.ByteString
import java.io.{ PipedInputStream, PipedOutputStream }
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import scala.collection.mutable.Queue

class RawTlsChannel(tlsActor: ActorRef) extends ByteChannel {
  lazy val toClientStream = new PipedOutputStream
  lazy val fromClientStream = new PipedInputStream

  lazy val toServerStream = new PipedOutputStream
  lazy val fromServerStream = new PipedInputStream

  var open = true

  val incomingBytes = new Queue[ByteBuffer]

  override def close: Unit = open = false

  override def isOpen: Boolean = open

  /**
   * Returns the number of bytes read into the given ByteBuffer. This reads
   * bytes passed from the client to the server in a non-blocking manner.
   *
   * @param buffer the ByteBuffer to read bytes into, if any space is available
   * @return the number of bytes read, which could be 0
   */
  override def read(buffer: ByteBuffer): Int =
    if (incomingBytes.isEmpty) 0
    else if (incomingBytes.head.remaining > buffer.remaining) {
      val toWrite = new Array[Byte](buffer.remaining)
      incomingBytes.head.get(toWrite)
      buffer.put(toWrite)
      toWrite.length
    }
    else {
      buffer.put(incomingBytes.head)
      incomingBytes.dequeue.capacity + read(buffer)
    }

  /**
   * Returns the number of bytes written from the given ByteBuffer. This
   * writes bytes from the server to the client in a non-blocking manner.
   *
   * @param buffer the ByteBuffer to write bytes from
   * @return the number of bytes written
   */
  override def write(buffer: ByteBuffer): Int = {
    val bytes = new Array[Byte](buffer.limit)
    for (i <- 0 until buffer.limit)
      bytes(i) = buffer.get
    tlsActor ! TlsActor.SendToClient(ByteString(bytes))
    bytes.length
  }

  def storeIncomingBytes(buffer: ByteBuffer): Unit = incomingBytes += buffer
}
