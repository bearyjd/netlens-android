package com.ventouxlabs.netlens.feature.speedtest.network

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ConnectivityManagerMeteredNetworkChecker @Inject constructor(
    @ApplicationContext context: Context,
) : MeteredNetworkChecker {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isMetered(): Boolean = connectivityManager.isActiveNetworkMetered
}
