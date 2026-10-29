package pl.ballistics.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.js.Date

object SettingsScreen {

    fun render(root: HTMLElement) {
        val ui = Store.data.ui
        if (!isStandalone()) root.card(S.sectionInstall) { el("p", "hint", S.hintInstall) }
        root.card(S.sectionAppearance) {
            select(S.labelTheme, listOf(ThemeMode.SYSTEM to S.themeSystem, ThemeMode.LIGHT to S.themeLight, ThemeMode.DARK to S.themeDark), ui.theme) { t ->
                Store.update { it.copy(ui = it.ui.copy(theme = t)) }
                App.applyTheme()
            }
        }
        Store.data.activeWeapon?.let { w ->
            root.card(S.sectionCorrections) {
                el("p", "hint", w.name)
                val now = ui.correction(w.reticleId)
                switch(S.labelDialElevation, now.dialElevation) { v -> setCorrection(w.reticleId) { it.copy(dialElevation = v) } }
                switch(S.labelDialWindage, now.dialWindage) { v -> setCorrection(w.reticleId) { it.copy(dialWindage = v) } }
            }
        }
        root.card(S.sectionBackup) {
            el("p", "hint", S.hintBackup)
            el("div", "actions start") {
                button(S.actionExport, "btn tonal") { export() }
                button(S.actionImport, "btn tonal") { import() }
            }
        }
        root.card(S.sectionAbout) {
            el("p", "hint", S.aboutWeb)
        }
    }

    private fun setCorrection(reticleId: String, change: (Correction) -> Correction) = Store.update {
        it.copy(ui = it.ui.copy(corrections = it.ui.corrections + (reticleId to change(it.ui.correction(reticleId)))))
    }

    /** Opened from the home screen icon rather than in a Safari tab. */
    private fun isStandalone(): Boolean =
        window.matchMedia("(display-mode: standalone)").matches || window.navigator.asDynamic().standalone == true

    private fun export() {
        val text = Store.encode(Store.data)
        val name = "ballistics-${Date().toISOString().take(10)}.json"
        val nav = window.navigator.asDynamic()
        // On an iPhone the share sheet can save to Files or send the file on; elsewhere it is downloaded.
        val file = js("new File([text], name, { type: 'application/json' })")
        val files = arrayOf(file)
        if (nav.canShare != undefined && nav.canShare(js("({ files: files })")) == true) {
            nav.share(js("({ files: files })")).catch { _: dynamic -> }
            return
        }
        val url = URL.createObjectURL(Blob(arrayOf(text), BlobPropertyBag(type = "application/json")))
        val a = document.createElement("a") as HTMLAnchorElement
        a.href = url
        a.download = name
        a.click()
        window.setTimeout({ URL.revokeObjectURL(url) }, 1000)
    }

    private fun import() {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "application/json,.json,text/plain"
        input.onchange = {
            input.files?.get(0)?.let { file ->
                val reader = FileReader()
                reader.onload = {
                    val data = Store.decode(reader.result as String)
                    if (data == null) {
                        toast(S.errorImport)
                    } else if (window.confirm(S.confirmImport)) {
                        Store.replace(data)
                        App.applyTheme()
                        toast(S.imported(data.weapons.size, data.ammo.size))
                        App.closeEditor()
                    }
                    null
                }
                reader.readAsText(file)
            }
            null
        }
        input.click()
    }
}
