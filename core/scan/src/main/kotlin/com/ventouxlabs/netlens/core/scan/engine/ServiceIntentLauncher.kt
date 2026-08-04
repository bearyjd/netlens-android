package com.ventouxlabs.netlens.core.scan.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ventouxlabs.netlens.core.scan.model.ServiceLaunch

/**
 * Fires a discovered service's URI as a view intent.
 *
 * Fires blind rather than pre-checking with `resolveActivity`: since Android 11 that query is
 * gated behind `<queries>` package visibility and would report "nothing handles ssh://" even on
 * a phone that has a terminal installed. Starting the intent and catching the miss gives the
 * right answer without the app declaring an interest in every scheme it might ever offer.
 */
object ServiceIntentLauncher {

    /** Returns false when no installed app handles the scheme, so the caller can say so. */
    fun launch(context: Context, launch: ServiceLaunch): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(launch.uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.d("ServiceLaunch", "No handler for ${launch.uri}", e)
            false
        }
    }
}
