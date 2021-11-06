package com.example

import scala.concurrent.duration._
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
    import DeviceManager._
    import Device.{ TemperatureRecorded, RecordTemperature }

    "DeviceManager actor" must {
        "be able to register device group and device actors" in {
            val probe = createTestProbe[DeviceRegistered]()
            val managerActor = spawn(DeviceManager())

            managerActor ! RequestTrackDevice("group", "device1", probe.ref)
            val registered1 = probe.receiveMessage()
            val deviceActor1 = registered1.device

            managerActor ! RequestTrackDevice("group", "device2", probe.ref)
            val registered2 = probe.receiveMessage()
            val deviceActor2 = registered2.device
            deviceActor1 should !==(deviceActor2)

            // check individual device actors
            val recordProbe = createTestProbe[TemperatureRecorded]()
            deviceActor1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref) 
            recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
            deviceActor2 ! RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
            recordProbe.expectMessage(TemperatureRecorded(requestId = 1))
        }

        "be able to register device group with device actors and confirming list" in {
            val probe = createTestProbe[DeviceRegistered]()
            val managerActor = spawn(DeviceManager())

            managerActor ! RequestTrackDevice("group", "device1", probe.ref)
            probe.receiveMessage()

            managerActor ! RequestTrackDevice("group", "device2", probe.ref)
            probe.receiveMessage()

            val deviceListProbe = createTestProbe[ReplyDeviceList]()
            managerActor ! RequestDeviceList(requestId = 0, groupId = "group", deviceListProbe.ref)
            deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1", "device2")))
        }

        "be able to register devices with different groups" in {
            val probe = createTestProbe[DeviceRegistered]()
            val managerActor = spawn(DeviceManager())

            managerActor ! RequestTrackDevice("group1", "device1", probe.ref)
            probe.receiveMessage()

            managerActor ! RequestTrackDevice("group2", "device2", probe.ref)
            probe.receiveMessage()

            val deviceListProbe = createTestProbe[ReplyDeviceList]()
            managerActor ! RequestDeviceList(requestId = 0, groupId = "group1", deviceListProbe.ref)
            deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1")))

            managerActor ! RequestDeviceList(requestId = 1, groupId = "group2", deviceListProbe.ref)
            deviceListProbe.expectMessage(ReplyDeviceList(requestId = 1, Set("device2")))
        }

        "check list for non-existent group" in {
            val probe = createTestProbe[ReplyDeviceList]()
            val managerActor = spawn(DeviceManager())
            managerActor ! RequestDeviceList(requestId = 0, groupId = "group", probe.ref)
            probe.expectMessage(ReplyDeviceList(requestId = 0, Set.empty))
        }

        "be able to list devices for active groups and empty for dead group" in {
            val probe = createTestProbe[DeviceRegistered]()
            val managerActor = spawn(DeviceManager())

            managerActor ! RequestTrackDevice("group1", "device1", probe.ref)
            probe.receiveMessage()

            managerActor ! RequestTrackDevice("group2", "device2", probe.ref)
            probe.receiveMessage()

            val deviceListProbe = createTestProbe[ReplyDeviceList]()
            managerActor ! RequestDeviceList(requestId = 0, groupId = "group1", deviceListProbe.ref)
            deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1")))

            managerActor ! RequestDeviceList(requestId = 1, groupId = "group2", deviceListProbe.ref)
            deviceListProbe.expectMessage(ReplyDeviceList(requestId = 1, Set("device2")))

            val groupShutDownProbe = createTestProbe[GroupRef]()
            managerActor ! GetGroupRef(groupId = "group1", groupShutDownProbe.ref)
            val groupActor = groupShutDownProbe.receiveMessage().group

            groupActor ! DeviceGroup.Passivate
            groupShutDownProbe.expectTerminated(groupActor, groupShutDownProbe.remainingOrDefault)

            groupShutDownProbe.awaitAssert {
                managerActor ! RequestDeviceList(requestId = 2, groupId = "group1", deviceListProbe.ref)
                deviceListProbe.expectMessage(ReplyDeviceList(requestId = 2, Set.empty))
            }
        }
    }
}