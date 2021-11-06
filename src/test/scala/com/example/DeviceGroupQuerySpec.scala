package com.example

import scala.concurrent.duration._
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceGroupQuerySpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
    import DeviceGroupQuery.{ WrappedRespondTemperature }
    import DeviceManager.{ RespondAllTemperatures, Temperature, TemperatureNotAvailable, DeviceNotAvailable, DeviceTimedOut }
    import Device.{ RespondTemperature, ReadTemperature, Command }

    "return temperature value for working devices" in {
        val requester = createTestProbe[RespondAllTemperatures]()

        val device1 = createTestProbe[Command]()
        val device2 = createTestProbe[Command]()

        val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

        val queryActor =
            spawn(DeviceGroupQuery(deviceIdToActor, requestId = 1, requester = requester.ref, timeout = 3.seconds))
        
        device1.expectMessageType[ReadTemperature]
        device2.expectMessageType[ReadTemperature]

        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device1", Some(1.0)))
        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device2", Some(2.0)))

        requester.expectMessage(
            RespondAllTemperatures(
                requestId = 1,
                temperatures = Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0))
            )
        )
    }

    "return TemperatureNotAvailable for devices with no readings" in {
        val requester = createTestProbe[RespondAllTemperatures]()

        val device1 = createTestProbe[Command]()
        val device2 = createTestProbe[Command]()

        val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

        val queryActor =
            spawn(DeviceGroupQuery(deviceIdToActor, requestId = 1, requester = requester.ref, timeout = 3.seconds))

        device1.expectMessageType[ReadTemperature]
        device2.expectMessageType[ReadTemperature]

        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device1", None))
        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device2", Some(2.0)))

        requester.expectMessage(
            RespondAllTemperatures(
                requestId = 1,
                temperatures = Map("device1" -> TemperatureNotAvailable, "device2" -> Temperature(2.0))
            )
        )
    }

    "return DeviceNotAvailable if device stops before answering" in {
        val requester = createTestProbe[RespondAllTemperatures]()

        val device1 = createTestProbe[Command]()
        val device2 = createTestProbe[Command]()

        val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

        val queryActor =
            spawn(DeviceGroupQuery(deviceIdToActor, requestId = 1, requester = requester.ref, timeout = 3.seconds))

        device1.expectMessageType[ReadTemperature]
        device2.expectMessageType[ReadTemperature]

        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device1", Some(2.0)))
        device2.stop()

        requester.expectMessage(
            RespondAllTemperatures(
                requestId = 1,
                temperatures = Map("device1" -> Temperature(2.0), "device2" -> DeviceNotAvailable)
            )
        )
    }

    "return temperature reading even if device stops after answering" in {
        val requester = createTestProbe[RespondAllTemperatures]()

        val device1 = createTestProbe[Command]()
        val device2 = createTestProbe[Command]()

        val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

        val queryActor =
            spawn(DeviceGroupQuery(deviceIdToActor, requestId = 1, requester = requester.ref, timeout = 3.seconds))
        
        device1.expectMessageType[ReadTemperature]
        device2.expectMessageType[ReadTemperature]

        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device1", Some(1.0)))
        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device2", Some(2.0)))

        device2.stop()

        requester.expectMessage(
            RespondAllTemperatures(
                requestId = 1,
                temperatures = Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0))
            )
        )
    }

    "return DeviceTimedOut if device does not answer in time" in {
        val requester = createTestProbe[RespondAllTemperatures]()

        val device1 = createTestProbe[Command]()
        val device2 = createTestProbe[Command]()

        val deviceIdToActor = Map("device1" -> device1.ref, "device2" -> device2.ref)

        val queryActor =
            spawn(DeviceGroupQuery(deviceIdToActor, requestId = 1, requester = requester.ref, timeout = 200.millis))
        
        device1.expectMessageType[ReadTemperature]
        device2.expectMessageType[ReadTemperature]

        queryActor ! WrappedRespondTemperature(RespondTemperature(requestId = 0, "device1", Some(1.0)))

        // no reply from device2

        requester.expectMessage(
            RespondAllTemperatures(
                requestId = 1,
                temperatures = Map("device1" -> Temperature(1.0), "device2" -> DeviceTimedOut)
            )
        )
    }
}