package pl.ballistics.web

import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement

/** Air, wind and the shot, shared by all profiles. */
object ConditionsScreen {

    fun render(root: HTMLElement) {
        val c = Store.data.conditions
        root.card(S.sectionAtmosphere) {
            row {
                numberField(S.labelTemperature, c.temperatureC, "°C") { v -> Store.updateConditions { it.copy(temperatureC = v) } }
                numberField(S.labelHumidity, c.humidityPct, "%") { v -> Store.updateConditions { it.copy(humidityPct = v) } }
            }
            row {
                numberField(S.labelPressure, c.pressureHpa, "hPa") { v -> Store.updateConditions { it.copy(pressureHpa = v) } }
                numberField(S.labelAltitude, c.altitudeM, "m") { v -> Store.updateConditions { it.copy(altitudeM = v) } }
            }
            switch(S.labelStationPressure, !c.pressureIsQnh) { v -> Store.updateConditions { it.copy(pressureIsQnh = !v) } }
        }
        root.card(S.sectionWind) {
            row {
                numberField(S.labelWindSpeed, c.windSpeed, "m/s") { v -> Store.updateConditions { it.copy(windSpeed = v) } }
                numberField(S.labelWindFrom, c.windDirectionDeg, "°") { v -> Store.updateConditions { it.copy(windFromDeg = v) } }
            }
            el("p", "hint", S.hintWindFrom)
        }
        root.card(S.sectionShot) { shotAngle(this) }
        root.card(S.sectionAdvanced) {
            switch(S.labelSpinDrift, c.spinDrift) { v -> Store.updateConditions { it.copy(spinDrift = v) } }
            switch(S.labelAeroJump, c.aerodynamicJump) { v -> Store.updateConditions { it.copy(aerodynamicJump = v) } }
            lateinit var coriolisFields: HTMLElement
            switch(S.labelCoriolis, c.coriolis) { v ->
                Store.updateConditions { it.copy(coriolis = v) }
                coriolisFields.style.display = if (v) "" else "none"
            }
            coriolisFields = el("div") {
                style.display = if (c.coriolis) "" else "none"
                lateinit var latitude: HTMLInputElement
                row {
                    latitude = numberField(S.labelLatitude, c.latitudeDeg, "°") { v -> Store.updateConditions { it.copy(latitudeDeg = v) } }
                    numberField(S.labelAzimuth, c.azimuthDeg, "°") { v -> Store.updateConditions { it.copy(azimuthDeg = v) } }
                }
                button(S.actionMyLocation, "btn tonal") {
                    val geo = window.navigator.asDynamic().geolocation
                    if (geo == null) {
                        toast(S.errorNoLocation)
                    } else {
                        geo.getCurrentPosition({ pos: dynamic ->
                            val lat = fmt(pos.coords.latitude as Double, 2)
                            latitude.value = lat
                            Store.updateConditions { it.copy(latitudeDeg = lat) }
                        }, { _: dynamic -> toast(S.errorNoLocation) })
                    }
                }
            }
        }
    }

    /** The shot angle and cant, with the camera to measure the angle; also on the target screen. */
    fun shotAngle(parent: HTMLElement) {
        val c = Store.data.conditions
        lateinit var angle: HTMLInputElement
        parent.row {
            angle = numberField(S.labelLookAngle, c.lookAngleDeg, "°") { v -> Store.updateConditions { it.copy(lookAngleDeg = v) } }
            numberField(S.labelCant, c.cantDeg, "°") { v -> Store.updateConditions { it.copy(cantDeg = v) } }
        }
        parent.el("button", "btn tonal") {
            setAttribute("type", "button")
            el("span", "btn-icon") { innerHTML = Icons.CAMERA }
            el("span", text = S.actionMeasureAngle)
            onclick = {
                AngleCamera.open { deg ->
                    angle.value = deg
                    Store.updateConditions { it.copy(lookAngleDeg = deg) }
                }
                null
            }
        }
    }
}
