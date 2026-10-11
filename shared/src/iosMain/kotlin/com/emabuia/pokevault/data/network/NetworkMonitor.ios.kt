package com.emabuia.pokevault.data.network

import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

// Tenuto qui: se il monitor venisse liberato smetterebbe di avvisare.
private var monitor: nw_path_monitor_t = null

/**
 * NWPathMonitor: "satisfied" vuol dire che il sistema vede una strada verso
 * Internet. Gli avvisi arrivano sulla coda principale.
 */
internal actual fun watchPlatformNetwork(onChange: (Boolean) -> Unit) {
    val created = nw_path_monitor_create()
    monitor = created
    nw_path_monitor_set_update_handler(created) { path ->
        onChange(nw_path_get_status(path) == nw_path_status_satisfied)
    }
    nw_path_monitor_set_queue(created, dispatch_get_main_queue())
    nw_path_monitor_start(created)
}
