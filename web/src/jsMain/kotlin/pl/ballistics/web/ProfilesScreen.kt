package pl.ballistics.web

import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/** Rifles and loads: pick the active rifle, open a profile to edit it. */
object ProfilesScreen {

    fun render(root: HTMLElement) {
        val data = Store.data
        val active = data.activeWeapon
        root.card(S.sectionRifles) {
            data.weapons.forEach { w ->
                el("div", if (w.id == active?.id) "list-item active" else "list-item") {
                    el("div", "list-text") {
                        el("div", "list-title", w.name)
                        el("div", "list-sub", listOfNotNull(w.caliber.ifBlank { null }, data.ammoFor(w)?.name).joinToString(" · "))
                    }
                    onclick = { e ->
                        // The Edit button inside the row opens the editor instead.
                        if ((e.target as? HTMLElement)?.closest("button") == null) {
                            Store.update { it.copy(activeWeaponId = w.id) }
                            App.closeEditor()
                        }
                        null
                    }
                    button(S.actionEdit, "btn text") { editWeapon(w, isNew = false) }
                }
            }
            button(S.actionAddRifle, "btn tonal") { editWeapon(WeaponProfile(ammoId = data.ammo.firstOrNull()?.id), isNew = true) }
        }
        root.card(S.sectionLoads) {
            data.ammo.forEach { a ->
                el("div", "list-item") {
                    el("div", "list-text") {
                        el("div", "list-title", a.name)
                        el("div", "list-sub", listOf(a.cartridge, "${a.massGr} gr", "${a.dragType} ${if (a.dragType == DragType.CUSTOM) "" else a.bc}").filter { it.isNotBlank() }.joinToString(" · "))
                    }
                    button(S.actionEdit, "btn text") { editAmmo(a, isNew = false) }
                }
            }
            button(S.actionAddLoad, "btn tonal") { editAmmo(AmmoProfile(), isNew = true) }
        }
    }

    private fun editWeapon(initial: WeaponProfile, isNew: Boolean) = App.openEditor(if (isNew) S.titleNewRifle else initial.name) { root ->
        var w = initial
        root.card(S.sectionRifle) {
            textField(S.labelName, w.name) { w = w.copy(name = it) }
            row {
                textField(S.labelCaliber, w.caliber) { w = w.copy(caliber = it) }
                numberField(S.labelTwist, w.twistIn, "in") { w = w.copy(twistIn = it) }
            }
            switch(S.labelRightTwist, w.rightHandTwist) { w = w.copy(rightHandTwist = it) }
        }
        root.card(S.sectionScope) {
            textField(S.labelScope, w.scopeName) { w = w.copy(scopeName = it) }
            row {
                select(S.labelTurretUnits, AngularUnit.entries.map { it to it.label }, w.angularUnit) { w = w.copy(angularUnit = it) }
                numberField(S.labelClickValue, w.clickValue) { w = w.copy(clickValue = it) }
            }
            numberField(S.labelSightHeight, w.sightHeightCm, "cm") { w = w.copy(sightHeightCm = it) }
        }
        root.card(S.sectionZero) {
            val loads = Store.data.ammo
            lateinit var velocity: org.w3c.dom.HTMLInputElement
            select(S.labelLoad, listOf<Pair<String?, String>>(null to "—") + loads.map { it.id to it.name }, w.ammoId) { id ->
                w = w.copy(ammoId = id)
                velocity.value = loads.find { it.id == id }?.let { w.measuredVelocity(it) }.orEmpty()
            }
            row {
                numberField(S.labelZeroRange, w.zeroRangeM, "m") { w = w.copy(zeroRangeM = it) }
                numberField(S.labelZeroTemperature, w.zeroTemperatureC, "°C") { w = w.copy(zeroTemperatureC = it) }
            }
            numberField(S.labelZeroPressure, w.zeroPressureHpa, "hPa") { w = w.copy(zeroPressureHpa = it) }
            velocity = numberField(S.labelRifleVelocity, loads.find { it.id == w.ammoId }?.let { w.measuredVelocity(it) }.orEmpty(), "m/s") { v ->
                loads.find { it.id == w.ammoId }?.let { w = w.withMeasuredVelocity(it, v) }
            }
        }
        editorButtons(root, isNew, onSave = {
            Store.update { d ->
                val list = if (isNew) d.weapons + w else d.weapons.map { if (it.id == w.id) w else it }
                d.copy(weapons = list, activeWeaponId = if (isNew) w.id else d.activeWeaponId)
            }
        }, onDelete = {
            Store.update { d -> d.copy(weapons = d.weapons.filterNot { it.id == w.id }) }
        })
    }

    private fun editAmmo(initial: AmmoProfile, isNew: Boolean) = App.openEditor(if (isNew) S.titleNewLoad else initial.name) { root ->
        var a = initial
        root.card(S.sectionLoad) {
            textField(S.labelName, a.name) { a = a.copy(name = it) }
            textField(S.labelCartridge, a.cartridge) { a = a.copy(cartridge = it) }
        }
        root.card(S.sectionBullet) {
            lateinit var bc: HTMLElement
            lateinit var curve: HTMLElement
            lateinit var bcInput: org.w3c.dom.HTMLInputElement
            fun showDrag() {
                bc.style.display = if (a.dragType == DragType.CUSTOM) "none" else ""
                curve.style.display = if (a.dragType == DragType.CUSTOM) "" else "none"
                bcInput.value = a.bc
            }
            select(S.labelDragModel, DragType.entries.map { it to if (it == DragType.CUSTOM) S.dragCustom else it.name }, a.dragType) {
                a = a.withDragType(it)
                showDrag()
            }
            bc = el("div") { bcInput = numberField(S.labelBc, a.bc) { a = a.copy(bc = it) } }
            curve = el("div") { textArea(S.labelCustomCurve, a.customCurve, "0.80 0.157") { a = a.copy(customCurve = it) } }
            showDrag()
            row {
                numberField(S.labelMass, a.massGr, "gr") { a = a.copy(massGr = it) }
                numberField(S.labelDiameter, a.diameterMm, "mm") { a = a.copy(diameterMm = it) }
            }
            numberField(S.labelBulletLength, a.lengthMm, "mm") { a = a.copy(lengthMm = it) }
        }
        root.card(S.sectionVelocity) {
            row {
                numberField(S.labelMuzzleVelocity, a.muzzleVelocity, "m/s") { a = a.copy(muzzleVelocity = it) }
                numberField(S.labelTestBarrel, a.testBarrelCm, "cm") { a = a.copy(testBarrelCm = it) }
            }
            row {
                numberField(S.labelPowderSensitivity, a.powderSensitivity, "m/s/°C") { a = a.copy(powderSensitivity = it) }
                numberField(S.labelAtTemperature, a.powderReferenceTemp, "°C") { a = a.copy(powderReferenceTemp = it) }
            }
            numberField(S.labelVelocitySd, a.velocitySd, "m/s") { a = a.copy(velocitySd = it) }
        }
        root.card(S.sectionNotes) {
            textArea(S.sectionNotes, a.notes, S.hintNotes) { a = a.copy(notes = it) }
        }
        editorButtons(root, isNew, onSave = {
            Store.update { d -> d.copy(ammo = if (isNew) d.ammo + a else d.ammo.map { if (it.id == a.id) a else it }) }
        }, onDelete = {
            Store.update { d ->
                d.copy(ammo = d.ammo.filterNot { it.id == a.id }, weapons = d.weapons.map { if (it.ammoId == a.id) it.copy(ammoId = null) else it })
            }
        })
    }

    private fun editorButtons(root: HTMLElement, isNew: Boolean, onSave: () -> Unit, onDelete: () -> Unit) {
        root.el("div", "actions") {
            if (!isNew) button(S.actionDelete, "btn danger") {
                if (window.confirm(S.confirmDelete)) {
                    onDelete()
                    App.closeEditor()
                }
            }
            button(S.actionCancel, "btn text") { App.closeEditor() }
            button(S.actionSave, "btn filled") {
                onSave()
                App.closeEditor()
            }
        }
    }
}
