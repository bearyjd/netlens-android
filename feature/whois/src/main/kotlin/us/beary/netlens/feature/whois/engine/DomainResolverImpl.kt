package us.beary.netlens.feature.whois.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

class DomainResolverImpl @Inject constructor() : DomainResolver {
    override suspend fun resolve(domain: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(domain).hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
