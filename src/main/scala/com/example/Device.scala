package com.example

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.LoggerOps
import akka.actor.typed.Signal
import akka.actor.typed.PostStop

object Device {
	def apply(groupId: String, deviceId: String): Behavior[Command] =
		Behaviors.setup(new Device(_, groupId, deviceId))

	sealed trait Command
	final case class ReadTemperature(requestId: Long, replyTo: ActorRef[RespondTemperature]) extends Command
	final case class RespondTemperature(requestId: Long, deviceId: String, value: Option[Double])

	final case class RecordTemperature(requestId: Long, value: Double, replyTo: ActorRef[TemperatureRecorded]) extends Command
	final case class TemperatureRecorded(requestId: Long)

	// For testing purposes
	case object Passivate extends Command
}

class Device(context: ActorContext[Device.Command], groupId: String, deviceId: String) extends AbstractBehavior[Device.Command](context) {
	import Device._
	var lastTemperatureReading: Option[Double] = None

	context.log.info2("Device actor {}-{} started", groupId, deviceId)

	override def onMessage(msg: Command): Behavior[Command] = {
		msg match {
			case RecordTemperature(requestId, value, replyTo) => 
				context.log.info2("Recorded temperature reading {} with {}", value, requestId)
				lastTemperatureReading = Some(value)
				replyTo ! TemperatureRecorded(requestId)
				this

			case ReadTemperature(requestId, replyTo) => 
				replyTo ! RespondTemperature(requestId, deviceId, lastTemperatureReading)
				this
			
			// For testing purposes
			case Passivate => 
				Behaviors.stopped
		}
	}

	override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
		case PostStop =>
			context.log.info2("Device actor {}-{} stopped", groupId, deviceId)
			this
	}
}