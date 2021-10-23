package com.example

import akka.actor.typed.Behavior
import akka.actor.typed.Signal
import akka.actor.typed.PostStop
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

object IotSupervisor {
	def apply(): Behavior[Nothing] =
		Behaviors.setup[Nothing](new IotSupervisor(_))
}

class IotSupervisor(context:ActorContext[Nothing]) extends AbstractBehavior[Nothing](context) {
	context.log.info("IoT Application started")

	override def onMessage(msg: Nothing): Behavior[Nothing] = {
		Behaviors.unhandled
	}

	override def onSignal: PartialFunction[Signal, Behavior[Nothing]] = {
		case PostStop =>
			context.log.info("IoT Application stopped")
			this
	}
}