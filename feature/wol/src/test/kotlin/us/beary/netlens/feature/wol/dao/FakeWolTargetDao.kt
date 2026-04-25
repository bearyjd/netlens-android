package us.beary.netlens.feature.wol.dao

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import us.beary.netlens.core.data.dao.WolTargetDao
import us.beary.netlens.core.data.model.WolTarget

class FakeWolTargetDao : WolTargetDao {
    private val targets = MutableStateFlow<List<WolTarget>>(emptyList())
    private var nextId = 1L

    override fun getAll(): Flow<List<WolTarget>> = targets

    override suspend fun insert(target: WolTarget) {
        val withId = target.copy(id = nextId++)
        targets.value = targets.value + withId
    }

    override suspend fun update(target: WolTarget) {
        targets.value = targets.value.map { if (it.id == target.id) target else it }
    }

    override suspend fun delete(target: WolTarget) {
        targets.value = targets.value.filter { it.id != target.id }
    }

    override suspend fun getById(id: Long): WolTarget? =
        targets.value.find { it.id == id }
}
