package com.example

import scala.concurrent.duration._
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceGroupSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
    import DeviceGroup._
    import DeviceManager.{ DeviceRegistered, RequestTrackDevice, RequestDeviceList, ReplyDeviceList }
    import Device.{ TemperatureRecorded, Passivate }

    "DeviceGroup actor" must {
        "be able to register a device actor" in {
            val probe = createTestProbe[DeviceRegistered]()
            val groupActor = spawn(DeviceGroup("group"))

            groupActor ! RequestTrackDevice("group", "device1", probe.ref)
            val registered1 = probe.receiveMessage()
            val deviceActor1 = registered1.device

            groupActor ! RequestTrackDevice("group", "device2", probe.ref)
            val registered2 = probe.receiveMessage()
            val deviceActor2 = registered2.device
            deviceActor1 should !==(deviceActor2)

            // check individual device actors
            val recordProbe = createTestProbe[TemperatureRecorded]()
            deviceActor1 ! Device.RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
            recordProbe.expectMessage(Device.TemperatureRecorded(requestId = 0))
            deviceActor2 ! Device.RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
            recordProbe.expectMessage(Device.TemperatureRecorded(requestId = 1))
        }

        "ignore requests from wrong group id" in {
            val probe = createTestProbe[DeviceRegistered]()
            val groupActor = spawn(DeviceGroup("group"))

            groupActor ! RequestTrackDevice("unknownGroup", "device1", probe.ref)
            probe.expectNoMessage(500.milliseconds)
        }

        "return same actor for some deviceId" in {
            val probe = createTestProbe[DeviceRegistered]()
            val groupActor = spawn(DeviceGroup("group"))

            groupActor ! RequestTrackDevice("group", "device1", probe.ref)
            val registered1 = probe.receiveMessage()
            
            groupActor ! RequestTrackDevice("group", "device1", probe.ref)
            val registered2 = probe.receiveMessage()

            registered1.device should ===(registered2.device)
        }

        "be able to list active devices" in {
            val probe = createTestProbe[DeviceRegistered]()
            val groupActor = spawn(DeviceGroup("group"))

            groupActor ! RequestTrackDevice("group", "device1", probe.ref)
            probe.receiveMessage()

            groupActor ! RequestTrackDevice("group", "device2", probe.ref)
            probe.receiveMessage()

            val deviceListProbe = createTestProbe[ReplyDeviceList]()
            groupActor ! RequestDeviceList(requestId = 0, groupId = "group", deviceListProbe.ref)
            deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1", "device2")))
        }

        "be able to list active devices after one shuts down" in {
            val probe = createTestProbe[DeviceRegistered]()
            val groupActor = spawn(DeviceGroup("group"))

            groupActor ! RequestTrackDevice("group", "device1", probe.ref)
            val registered1 = probe.receiveMessage()
            val toShutDownActor = registered1.device

            groupActor ! RequestTrackDevice("group", "device2", probe.ref)
            probe.receiveMessage()

            val deviceListProbe = createTestProbe[ReplyDeviceList]()
            groupActor ! RequestDeviceList(requestId = 0, groupId = "group", deviceListProbe.ref)
            deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1", "device2")))

            toShutDownActor ! Passivate
            probe.expectTerminated(toShutDownActor, probe.remainingOrDefault)

            probe.awaitAssert {
                groupActor ! RequestDeviceList(requestId = 1, groupId = "group", deviceListProbe.ref)
                deviceListProbe.expectMessage(ReplyDeviceList(requestId = 1, Set("device2")))
            }
        }

        "be able to collect temperatures from all active devices" in {
            val registeredProbe = createTestProbe[DeviceRegistered]()
            val groupActor = spawn(DeviceGroup("group"))

            groupActor ! RequestTrackDevice("group", "device1", registeredProbe.ref)
            val deviceActor1 = registeredProbe.receiveMessage().device

            groupActor ! RequestTrackDevice("group", "device2", registeredProbe.ref)
            val deviceActor2 = registeredProbe.receiveMessage().device

            groupActor ! RequestTrackDevice("group", "device3", registeredProbe.ref)
            registeredProbe.receiveMessage()

            // Check that the device actors are working
            val recordProbe = createTestProbe[TemperatureRecorded]()
            deviceActor1 ! Device.RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
            recordProbe.expectMessage(TemperatureRecorded(requestId = 0))

            deviceActor2 ! Device.RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
            recordProbe.expectMessage(TemperatureRecorded(requestId = 1))

            val allTempProbe = createTestProbe[DeviceManager.RespondAllTemperatures]()
            groupActor ! DeviceManager.RequestAllTemperatures(requestId = 0, groupId = "group", allTempProbe.ref)
            allTempProbe.expectMessage(
                DeviceManager.RespondAllTemperatures(
                    requestId = 0,
                    temperatures =
                        Map("device1" -> DeviceManager.Temperature(1.0), "device2" -> DeviceManager.Temperature(2.0), "device3" -> DeviceManager.TemperatureNotAvailable)
                )
            )
        }   
    }
}