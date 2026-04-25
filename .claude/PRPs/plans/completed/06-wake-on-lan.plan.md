# Plan: Wake-on-LAN

## Summary

Implement a Wake-on-LAN tool that sends magic packets to wake devices, with a saved targets list persisted in Room for quick access.

## User Story

As a user, I want to send Wake-on-LAN magic packets to saved devices, so that I can remotely power on machines on my local network.

## Metadata

- **Complexity**: Medium
- **Branch**: feat/wol
- **PR**: PR-06
- **Depends On**: scaffold
- **Estimated Files**: 12
- **New Modules**: none (feature/wol already exists with :core:data dep)

## Patterns to Mirror

### FEATURE_MODULE
// SOURCE: feature/wol/build.gradle.kts — depends on :core:network, :core:data

### ROOM_ENTITY
// Same pattern as PR-02 for adding entity/DAO to NetLensDatabase

## Files to Change

| File | Action | Description |
|------|--------|-------------|
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/entity/WolTargetEntity.kt` | CREATE | @Entity: id (auto PK), name, mac, broadcastIp, port |
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/dao/WolTargetDao.kt` | CREATE | @Dao: getAll Flow, insert, update, delete |
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/NetLensDatabase.kt` | UPDATE | Add WolTargetEntity, bump version, add abstract wolTargetDao() |
| `core/data/src/main/kotlin/com.ventoux.netlens/core/data/di/DataModule.kt` | UPDATE | @Provides WolTargetDao |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/model/WolTarget.kt` | CREATE | Domain data class: id, name, mac, broadcastIp, port |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/model/WolUiState.kt` | CREATE | data class: targets list, isSending, lastSentTarget, error, showAddDialog |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/engine/WolSender.kt` | CREATE | Interface: suspend fun send(mac: String, broadcastIp: String, port: Int): Result<Unit> |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/engine/WolSenderImpl.kt` | CREATE | Build 102-byte magic packet (6x 0xFF + 16x MAC bytes). DatagramSocket + DatagramPacket to broadcastIp:port. Acquire MulticastLock from WifiManager. |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/data/WolRepository.kt` | CREATE | Interface: getTargets Flow, addTarget, updateTarget, deleteTarget, sendWol |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/data/WolRepositoryImpl.kt` | CREATE | Combines WolTargetDao + WolSender, maps entities |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/di/WolModule.kt` | CREATE | @Module binding sender + repository |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/WolViewModel.kt` | CREATE | @HiltViewModel, CRUD targets, send WoL, show snackbar state |
| `feature/wol/src/main/kotlin/com.ventoux.netlens/feature/wol/WolScreen.kt` | CREATE | LazyColumn of saved targets with send button per item, FAB to add, AlertDialog for add/edit (name, MAC, broadcast IP, port fields), snackbar on send |
| `app/src/main/kotlin/com.ventoux.netlens/navigation/NetLensNavHost.kt` | UPDATE | Replace PlaceholderScreen for Wol route |

## Step-by-Step Tasks

### Task 1: Create Room entity + DAO
- **ACTION**: `WolTargetEntity` with `@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val mac: String, val broadcastIp: String, val port: Int`. DAO with `@Query("SELECT * FROM wol_targets ORDER BY name") fun getAll(): Flow<List<WolTargetEntity>>`, `@Insert suspend fun insert(target: WolTargetEntity)`, `@Update suspend fun update(target: WolTargetEntity)`, `@Delete suspend fun delete(target: WolTargetEntity)`.
- **VALIDATE**: Compiles

### Task 2: Update NetLensDatabase + DataModule
- **ACTION**: Add WolTargetEntity to entities, bump version (coordinate with PR-02 if parallel — may need version 3), add abstract `wolTargetDao()`. Add `@Provides` in DataModule.
- **VALIDATE**: Compiles

### Task 3: Create WolSender
- **ACTION**: Interface + impl. Build magic packet: `ByteArray(102)`. Fill first 6 bytes with `0xFF`. Parse MAC string (strip colons/dashes), convert to 6 bytes, repeat 16 times starting at index 6. Send via `DatagramSocket().use { socket -> socket.broadcast = true; socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(broadcastIp), port)) }`. Wrap in `runCatching`. Acquire/release `WifiManager.MulticastLock`.
- **VALIDATE**: Unit test (verify packet structure)

### Task 4: Create WolRepository
- **ACTION**: Interface with `fun getTargets(): Flow<List<WolTarget>>`, `suspend fun addTarget(target: WolTarget)`, `suspend fun deleteTarget(target: WolTarget)`, `suspend fun sendWol(target: WolTarget): Result<Unit>`. Impl maps entities to domain.
- **VALIDATE**: Compiles

### Task 5: Create DI module
- **ACTION**: `WolModule` with `@Binds` for sender + repository
- **VALIDATE**: Compiles

### Task 6: Create WolViewModel
- **ACTION**: `@HiltViewModel`, collects targets from repository. `addTarget()`, `deleteTarget()`, `sendWol(target)` with snackbar state (`lastSentTarget` + reset after delay). Dialog open/close state.
- **VALIDATE**: Unit test with Turbine

### Task 7: Create WolScreen
- **ACTION**: LazyColumn of target cards: name, MAC, broadcast IP. Each card has send IconButton (power icon). Swipe-to-delete. FAB opens add dialog. AlertDialog with name, MAC (formatted input), broadcast IP (default 255.255.255.255), port (default 9) fields. Snackbar "Magic packet sent to [name]".
- **VALIDATE**: Preview renders

### Task 8: Wire navigation
- **ACTION**: Update NetLensNavHost for Wol route
- **VALIDATE**: `./gradlew assembleDebug`

## Testing Strategy

- **Unit tests for**:
  - `WolSenderImpl` — magic packet byte structure (verify 6xFF + 16x MAC)
  - `WolViewModel` — Turbine: target CRUD, send state
- **Integration tests for**:
  - `WolTargetDao` — Room in-memory: insert, getAll flow, delete

## Validation
