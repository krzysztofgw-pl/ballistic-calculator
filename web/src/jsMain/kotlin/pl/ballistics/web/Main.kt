package pl.ballistics.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

fun main() {
    window.onload = { App.start(); null }
}

private enum class Tab(val title: () -> String, val icon: String) {
    PROFILES({ S.tabProfiles }, Icons.PERSON),
    CONDITIONS({ S.tabConditions }, Icons.PLACE),
    TABLE({ S.tabTable }, Icons.LIST),
    TARGET({ S.tabTarget }, Icons.TARGET),
}

/** The shell: a top bar, the open screen, and the tabs at the bottom within reach of the thumb. */
object App {
    private lateinit var titleBar: HTMLElement
    private lateinit var actionBar: HTMLElement
    private lateinit var content: HTMLElement
    private lateinit var tabs: HTMLElement
    private var tab = Tab.TABLE

    fun start() {
        val root = document.getElementById("app") as HTMLElement
        root.clear()
        root.el("header", "top-bar") {
            titleBar = el("h1")
            actionBar = el("div", "top-action")
        }
        content = root.el("main", "content")
        tabs = root.el("nav", "tabs")
        applyTheme()
        showTab(tab)
        registerServiceWorker()
    }

    fun applyTheme() {
        val theme = when (Store.data.ui.theme) {
            ThemeMode.SYSTEM -> null
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
        }
        if (theme == null) document.documentElement!!.removeAttribute("data-theme")
        else document.documentElement!!.setAttribute("data-theme", theme)
    }

    private fun showTab(t: Tab) {
        tab = t
        open(t.title(), back = null) { container ->
            when (t) {
                Tab.PROFILES -> ProfilesScreen.render(container)
                Tab.CONDITIONS -> ConditionsScreen.render(container)
                Tab.TABLE -> TableScreen.render(container)
                Tab.TARGET -> TargetScreen.render(container)
            }
        }
    }

    /** Back to the tab that was open before an editor or the settings. */
    fun closeEditor() = showTab(tab)

    /** Opens a full-screen editor over the tabs; [back] returns from it. */
    fun openEditor(name: String, render: (HTMLElement) -> Unit) = open(name, back = { closeEditor() }, render = render)

    private fun open(name: String, back: (() -> Unit)?, render: (HTMLElement) -> Unit) {
        Store.clearListeners()
        titleBar.clear()
        if (back != null) {
            titleBar.el("button", "icon-btn") {
                setAttribute("type", "button")
                setAttribute("aria-label", S.actionBack)
                innerHTML = Icons.BACK
                onclick = { back(); null }
            }
        }
        titleBar.el("span", text = name)
        actionBar.clear()
        if (back == null) {
            actionBar.el("button", "icon-btn") {
                setAttribute("type", "button")
                setAttribute("aria-label", S.titleSettings)
                innerHTML = Icons.SETTINGS
                onclick = { openEditor(S.titleSettings) { SettingsScreen.render(it) }; null }
            }
        }
        tabs.clear()
        tabs.style.display = if (back == null) "" else "none"
        if (back == null) {
            Tab.entries.forEach { t ->
                tabs.el("button", if (t == tab) "tab active" else "tab") {
                    setAttribute("type", "button")
                    el("span", "tab-icon") { innerHTML = t.icon }
                    el("span", "tab-label", t.title())
                    onclick = { showTab(t); null }
                }
            }
        }
        content.clear()
        content.scrollTop = 0.0
        window.scrollTo(0.0, 0.0)
        render(content)
    }

    private fun registerServiceWorker() {
        val sw = window.navigator.asDynamic().serviceWorker ?: return
        // Offline use after the first visit; failing to register only loses that.
        runCatching { sw.register("sw.js") }
    }
}

/** Material icons, as inline SVG so they work offline and follow the text colour. */
object Icons {
    private fun svg(path: String) = """<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path fill="currentColor" d="$path"/></svg>"""

    val PERSON = svg("M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z")
    val PLACE = svg("M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z")
    val LIST = svg("M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z")
    val TARGET = svg("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 17.93V17h-2v2.93A8 8 0 0 1 4.07 13H7v-2H4.07A8 8 0 0 1 11 4.07V7h2V4.07A8 8 0 0 1 19.93 11H17v2h2.93A8 8 0 0 1 13 19.93zM12 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z")
    val SETTINGS = svg("M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.48.48 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96a.49.49 0 0 0-.59.22L2.74 8.87a.48.48 0 0 0 .12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.48.48 0 0 0-.12-.61l-2.01-1.58zM12 15.6A3.6 3.6 0 1 1 12 8.4a3.6 3.6 0 0 1 0 7.2z")
    val BACK = svg("M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z")
    val CAMERA = svg("M12 15.2a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4zM9 2 7.17 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2h-3.17L15 2H9z")
    val WARNING = svg("M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z")
}
