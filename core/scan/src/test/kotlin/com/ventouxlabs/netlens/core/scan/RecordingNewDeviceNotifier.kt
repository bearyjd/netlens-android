package com.ventouxlabs.netlens.core.scan

import com.ventouxlabs.netlens.core.data.model.KnownDeviceEntity

class RecordingNewDeviceNotifier : NewDeviceNotifier {
    val notified = mutableListOf<KnownDeviceEntity>()
    override fun createChannel() {}
    override fun notify(device: KnownDeviceEntity) { notified.add(device) }
}
