package pl.ballistics.web

import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.Node

/*
 * A few helpers for building the UI straight on the DOM. Screens are rebuilt when they
 * open; an input only updates the data, and the parts that show results redraw themselves.
 */

fun Node.el(tag: String, cls: String? = null, text: String? = null, block: HTMLElement.() -> Unit = {}): HTMLElement {
    val e = document.createElement(tag) as HTMLElement
    if (cls != null) e.className = cls
    if (text != null) e.textContent = text
    appendChild(e)
    e.block()
    return e
}

fun HTMLElement.clear() {
    while (firstChild != null) removeChild(firstChild!!)
}

/** A titled card that groups fields, like the sections of the Android screens. */
fun Node.card(title: String? = null, block: HTMLElement.() -> Unit): HTMLElement = el("section", "card") {
    if (title != null) el("h2", text = title)
    block()
}

/** Fields placed side by side, two to a row on a phone. */
fun Node.row(block: HTMLElement.() -> Unit): HTMLElement = el("div", "row", block = block)

private fun Node.field(label: String, block: HTMLElement.() -> Unit): HTMLElement = el("label", "field") {
    el("span", "field-label", label)
    block()
}

fun Node.numberField(label: String, value: String, unit: String? = null, onChange: (String) -> Unit): HTMLInputElement {
    lateinit var input: HTMLInputElement
    field(label) {
        el("div", "input-wrap") {
            input = el("input") {
                this as HTMLInputElement
                type = "text"
                setAttribute("inputmode", "decimal")
                setAttribute("autocomplete", "off")
                this.value = value
                oninput = { onChange(this.value); null }
            } as HTMLInputElement
            if (unit != null) el("span", "unit", unit)
        }
    }
    return input
}

fun Node.textField(label: String, value: String, onChange: (String) -> Unit): HTMLInputElement {
    lateinit var input: HTMLInputElement
    field(label) {
        input = el("input") {
            this as HTMLInputElement
            type = "text"
            this.value = value
            oninput = { onChange(this.value); null }
        } as HTMLInputElement
    }
    return input
}

fun Node.textArea(label: String, value: String, placeholder: String = "", onChange: (String) -> Unit): HTMLTextAreaElement {
    lateinit var input: HTMLTextAreaElement
    field(label) {
        input = el("textarea") {
            this as HTMLTextAreaElement
            this.value = value
            this.placeholder = placeholder
            rows = 5
            oninput = { onChange(this.value); null }
        } as HTMLTextAreaElement
    }
    return input
}

fun <T> Node.select(label: String, options: List<Pair<T, String>>, value: T, onChange: (T) -> Unit): HTMLSelectElement {
    lateinit var s: HTMLSelectElement
    field(label) {
        s = el("select") {
            this as HTMLSelectElement
            options.forEachIndexed { i, (v, text) ->
                el("option", text = text) {
                    setAttribute("value", i.toString())
                    if (v == value) setAttribute("selected", "")
                }
            }
            onchange = { onChange(options[selectedIndex].first); null }
        } as HTMLSelectElement
    }
    return s
}

fun Node.switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit): HTMLInputElement {
    lateinit var input: HTMLInputElement
    el("label", "switch") {
        el("span", text = label)
        input = el("input") {
            this as HTMLInputElement
            type = "checkbox"
            this.checked = checked
            onchange = { onChange(this.checked); null }
        } as HTMLInputElement
        el("span", "track")
    }
    return input
}

fun Node.button(label: String, cls: String = "btn", onClick: () -> Unit): HTMLButtonElement =
    el("button", cls, label) {
        setAttribute("type", "button")
        onclick = { onClick(); null }
    } as HTMLButtonElement

/** [x] with [digits] decimals; Kotlin/JS has no String.format. */
fun fmt(x: Double, digits: Int): String {
    val text = x.asDynamic().toFixed(digits) as String
    // "-0.00" reads as a correction; show it as plain zero.
    return if (text.startsWith("-") && text.trimStart('-', '0', '.').isEmpty()) text.drop(1) else text
}

/** A short message at the bottom of the screen, like an Android toast. */
fun toast(text: String) {
    val t = document.body!!.el("div", "toast", text)
    kotlinx.browser.window.setTimeout({ t.remove() }, 2500)
}
