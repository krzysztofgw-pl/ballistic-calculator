package pl.ballistics.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos

/**
 * The shot angle from the phone's tilt while the rear camera looks at the target through a crosshair.
 * Safari asks for motion access on the first use; that request has to come from the tap itself.
 */
object AngleCamera {

    fun open(onUse: (String) -> Unit) {
        val permission = window.asDynamic().DeviceOrientationEvent?.requestPermission
        if (permission != null) {
            window.asDynamic().DeviceOrientationEvent.requestPermission().then { state: String ->
                if (state == "granted") show(onUse) else toast(S.errorMotionDenied)
            }.catch { _: dynamic -> toast(S.errorMotionDenied) }
        } else {
            show(onUse)
        }
    }

    private fun show(onUse: (String) -> Unit) {
        var stream: dynamic = null
        var angle: Double? = null
        var closed = false
        lateinit var overlay: HTMLElement
        lateinit var readout: HTMLElement
        lateinit var use: HTMLElement

        val listener: (Event) -> Unit = { e ->
            val beta = e.asDynamic().beta as Double?
            val gamma = e.asDynamic().gamma as Double?
            if (beta != null && gamma != null) {
                // The camera looks along the back of the phone (its −z axis); its elevation from the
                // device orientation angles (W3C Z-X'-Y'' convention) is asin(−cos β · cos γ).
                val raw = asin(-cos(beta * PI / 180) * cos(gamma * PI / 180)) * 180 / PI
                // A light low-pass filter steadies the reading of a hand-held phone.
                angle = angle?.let { it + (raw - it) * 0.2 } ?: raw
                val shown = fmt(angle!!, 1)
                readout.textContent = "$shown°"
                use.textContent = S.actionUseAngle(shown)
                use.removeAttribute("disabled")
            }
        }

        fun close() {
            closed = true
            window.removeEventListener("deviceorientation", listener)
            stream?.getTracks()?.forEach { t: dynamic -> t.stop() }
            overlay.remove()
        }

        overlay = document.body!!.el("div", "camera") {
            val video = el("video") {
                this as HTMLVideoElement
                setAttribute("playsinline", "")
                setAttribute("autoplay", "")
                muted = true
            } as HTMLVideoElement
            el("div", "crosshair")
            readout = el("div", "camera-readout", "—")
            el("p", "camera-hint", S.hintAngleCamera)
            el("div", "camera-actions") {
                button(S.actionClose, "btn tonal") { close() }
                use = button(S.actionUseAngle("—"), "btn filled") {
                    angle?.let { a ->
                        onUse(fmt(a, 1))
                        close()
                    }
                }
                use.setAttribute("disabled", "")
            }
            val media = window.navigator.asDynamic().mediaDevices
            if (media?.getUserMedia != null) {
                media.getUserMedia(js("({ video: { facingMode: 'environment' }, audio: false })")).then { s: dynamic ->
                    stream = s
                    if (closed) s.getTracks().forEach { t: dynamic -> t.stop() }
                    video.asDynamic().srcObject = s
                    video.play()
                }.catch { _: dynamic -> toast(S.errorNoCamera) }
            } else {
                toast(S.errorNoCamera)
            }
        }
        window.addEventListener("deviceorientation", listener)
    }
}
