package pl.ballistics.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.ballistics.app.data.AmmoProfile
import pl.ballistics.app.data.AppData
import pl.ballistics.app.data.CloudSync
import pl.ballistics.app.data.SyncState
import pl.ballistics.app.data.Conditions
import pl.ballistics.app.data.Repository
import pl.ballistics.app.data.WeaponProfile

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository(application)

    private val _data = MutableStateFlow(repository.load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _result = MutableStateFlow<CalcResult?>(null)
    val result: StateFlow<CalcResult?> = _result.asStateFlow()

    private var job: Job? = null
    private var pushJob: Job? = null

    private val cloud = CloudSync(application, ::onRemoteData).also { c ->
        c.pendingFirstPush = { c.push(_data.value) }
    }
    val syncState: StateFlow<SyncState> = cloud.state

    init {
        recalculate(debounce = false)
    }

    private fun update(transform: (AppData) -> AppData) {
        _data.value = transform(_data.value).copy(modifiedAt = System.currentTimeMillis())
        repository.save(_data.value)
        recalculate(debounce = true)
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(1500)
            cloud.push(_data.value)
        }
    }

    /** A newer copy arrived from another device; an older one means ours should go up. */
    private fun onRemoteData(remote: AppData) {
        val local = _data.value
        when {
            remote.modifiedAt > local.modifiedAt -> {
                _data.value = remote
                repository.save(remote)
                recalculate(debounce = false)
            }
            remote.modifiedAt < local.modifiedAt -> cloud.push(local)
        }
    }

    suspend fun signIn(activity: android.content.Context): String? = cloud.signIn(activity)

    fun signOut() {
        viewModelScope.launch { cloud.signOut() }
    }

    fun updateConditions(transform: (Conditions) -> Conditions) = update { it.copy(conditions = transform(it.conditions)) }

    fun selectWeapon(id: String) = update { it.copy(activeWeaponId = id) }

    fun saveWeapon(weapon: WeaponProfile) = update { d ->
        val exists = d.weapons.any { it.id == weapon.id }
        d.copy(
            weapons = if (exists) d.weapons.map { if (it.id == weapon.id) weapon else it } else d.weapons + weapon,
            activeWeaponId = if (exists) d.activeWeaponId else weapon.id,
        )
    }

    fun deleteWeapon(id: String) = update { d ->
        val weapons = d.weapons.filterNot { it.id == id }
        d.copy(weapons = weapons, activeWeaponId = if (d.activeWeaponId == id) weapons.firstOrNull()?.id else d.activeWeaponId)
    }

    fun saveAmmo(ammo: AmmoProfile) = update { d ->
        val exists = d.ammo.any { it.id == ammo.id }
        d.copy(ammo = if (exists) d.ammo.map { if (it.id == ammo.id) ammo else it } else d.ammo + ammo)
    }

    fun deleteAmmo(id: String) = update { d ->
        d.copy(
            ammo = d.ammo.filterNot { it.id == id },
            weapons = d.weapons.map { if (it.ammoId == id) it.copy(ammoId = null) else it },
        )
    }

    /** Replaces everything with imported data; returns false if the file is not a valid export. */
    fun importData(text: String): Boolean {
        val imported = Repository.decode(text) ?: return false
        update { imported }
        return true
    }

    fun exportData(): String = Repository.encode(_data.value)

    private fun recalculate(debounce: Boolean) {
        job?.cancel()
        val d = _data.value
        job = viewModelScope.launch {
            if (debounce) delay(250)
            val weapon = d.activeWeapon
            _result.value = withContext(Dispatchers.Default) {
                Calculator.calculate(weapon, weapon?.let { d.ammoFor(it) }, d.conditions)
            }
        }
    }
}
