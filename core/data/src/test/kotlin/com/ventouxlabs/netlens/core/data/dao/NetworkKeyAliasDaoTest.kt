package com.ventouxlabs.netlens.core.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ventouxlabs.netlens.core.data.NetLensDatabase
import com.ventouxlabs.netlens.core.data.model.NetworkKeyAliasEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real-SQL test of the alias pruning the canonicaliser relies on. */
@RunWith(RobolectricTestRunner::class)
class NetworkKeyAliasDaoTest {
    private lateinit var db: NetLensDatabase
    private lateinit var dao: NetworkKeyAliasDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NetLensDatabase::class.java).build()
        dao = db.networkKeyAliasDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `pruning keeps the newest gateway id aliases, the canonical row, MAC aliases and other networks`() = runBlocking {
        dao.insertIfNew(NetworkKeyAliasEntity("gw-mac:a", "gw-mac:a", 1))
        dao.insertIfNew(NetworkKeyAliasEntity("gw-mac:a2", "gw-mac:a", 1))
        for (n in 1..5) dao.insertIfNew(NetworkKeyAliasEntity("gw-id:g$n", "gw-mac:a", n.toLong()))
        dao.insertIfNew(NetworkKeyAliasEntity("gw-id:other", "gw-mac:b", 1))

        dao.pruneGatewayIdAliases("gw-mac:a", keep = 2)

        assertEquals("gw-mac:a", dao.canonicalFor("gw-mac:a"))
        assertEquals("gw-mac:a", dao.canonicalFor("gw-mac:a2"))
        assertEquals("gw-mac:a", dao.canonicalFor("gw-id:g5"))
        assertEquals("gw-mac:a", dao.canonicalFor("gw-id:g4"))
        assertEquals(null, dao.canonicalFor("gw-id:g3"))
        assertEquals(null, dao.canonicalFor("gw-id:g1"))
        assertEquals("gw-mac:b", dao.canonicalFor("gw-id:other"))
    }
}
