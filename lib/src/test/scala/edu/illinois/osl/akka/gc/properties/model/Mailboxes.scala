package edu.illinois.osl.akka.gc.properties.model

import scala.collection.mutable
import edu.illinois.osl.akka.gc.interfaces.Pretty
import edu.illinois.osl.akka.gc.interfaces.Pretty._

trait Mailbox[T <: Pretty] extends Pretty {
  def add(message: T, sender: Name): Unit
  def nonEmpty: Boolean
  def isEmpty: Boolean
  def toIterable: Iterable[T]
  /** The set of messages that can be delivered next */
  def next: Iterable[T]
  /** Pull the message out of the mailbox */
  def deliverMessage(msg: T): Unit
}

/**
 * A collection of undelivered messages sent to some actor. These messages
 * have FIFO (rather than causal) semantics.
 */
class FIFOMailbox[T <: Pretty] extends Mailbox[T] {

  private var messagesFrom: Map[Name, mutable.Queue[T]] = Map()

  /** The collection of actors from which there are undelivered messages */
  def senders: Iterable[Name] = messagesFrom.keys

  /** Returns true iff there are any undelivered messages */
  def nonEmpty: Boolean = messagesFrom.nonEmpty

  /** Returns true iff there are no undelivered messages */
  def isEmpty: Boolean = messagesFrom.isEmpty

  def toIterable: Iterable[T] = messagesFrom.values.flatMap(_.toIterable)

  def next: Iterable[T] =
    for {
      sender <- messagesFrom.keys;
      if messagesFrom(sender).nonEmpty
    } yield messagesFrom(sender).front

  def deliverMessage(msg: T): Unit = {
    val sender = for {
      sender <- messagesFrom.keys;
      if messagesFrom(sender).headOption == Some(msg)
    } yield sender
    assert(sender.size > 0, s"Can't find $msg in mailbox $messagesFrom")
    assert(sender.size == 1, s"Found duplicate messages $msg in $messagesFrom")
    deliverFrom(sender.head)
  }

  def add(message: T, sender: Name): Unit = {
    val queue = messagesFrom.getOrElse(sender, mutable.Queue())
    queue.enqueue(message)
    messagesFrom += (sender -> queue)
  }

  def deliverFrom(sender: Name): Unit = {
    val mailbox = messagesFrom(sender)
    mailbox.dequeue()
    if (mailbox.isEmpty)
      messagesFrom -= sender
  }

  override def pretty: String = 
    "[" + messagesFrom.map{ 
      case (sender, msgs) => s"from ${sender.pretty}: ${msgs.toList.pretty}" 
    }.toList.mkString("; ") + "]"
}
