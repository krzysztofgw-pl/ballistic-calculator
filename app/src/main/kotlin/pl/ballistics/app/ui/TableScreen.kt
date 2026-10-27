package pl.ballistics.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.ballistics.app.CalcResult
import pl.ballistics.core.TrajectoryPoint
import kotlin.math.abs
import kotlin.math.roundToInt

private val columns = listOf(
    "m" to 52, "Opad cm" to 72, "Popr." to 68, "Klik" to 56,
    "Znos cm" to 72, "Popr." to 68, "Klik" to 56,
    "m/s" to 56, "J" to 64, "s" to 56, "Mach" to 52,
)

@Composable
fun TableScreen(result: CalcResult?, modifier: Modifier = Modifier) {
    when (result) {
        null -> Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.padding(32.dp))
        }
        is CalcResult.Error -> Text(
            result.message,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(16.dp),
        )
        is CalcResult.Ok -> Table(result, modifier)
    }
}

@Composable
private fun Table(result: CalcResult.Ok, modifier: Modifier) {
    val s = result.solution
    val unit = result.angularUnit
    val click = result.clickValue

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${result.weapon.name} · ${result.ammo.name}", style = MaterialTheme.typography.titleSmall)
            val summary = buildList {
                add("V0 ${s.muzzleVelocityMps.roundToInt()} m/s")
                add("wys. gęstościowa ${s.densityAltitudeM.roundToInt()} m")
                s.stabilityFactor?.let { add("Sg %.2f".format(it)) }
            }
            Text(summary.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
            s.stabilityFactor?.let {
                if (it < 1.0) Warning("Pocisk niestabilny (Sg < 1). Wynik niewiarygodny.")
                else if (it < 1.4) Warning("Stabilność marginalna (Sg < 1,4).")
            }
            Text(
                "Poprawki w ${unit.label}: ↑ w górę, P/L w prawo/lewo. Klik = ${result.weapon.clickValue} ${unit.label}.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val scroll = rememberScrollState()
        LazyColumn(Modifier.horizontalScroll(scroll)) {
            item {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 6.dp)) {
                    columns.forEach { (title, w) -> Cell(title, w, bold = true) }
                }
            }
            items(s.points) { p -> PointRow(p, unit.radians, click) }
        }
    }
}

@Composable
private fun Warning(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PointRow(p: TrajectoryPoint, unitRad: Double, click: Double?) {
    // Correction is the angle to dial: opposite to the impact offset.
    val up = -p.dropRad / unitRad
    val right = -p.windageRad / unitRad
    val cells = listOf(
        p.rangeM.roundToInt().toString(),
        "%.1f".format(p.dropM * 100),
        if (p.rangeM > 0) "↑%.2f".format(up) else "",
        if (p.rangeM > 0 && click != null) (up / click).roundToInt().toString() else "",
        "%.1f".format(p.windageM * 100),
        if (p.rangeM > 0) side(right) + "%.2f".format(abs(right)) else "",
        if (p.rangeM > 0 && click != null) side(right) + (abs(right) / click).roundToInt() else "",
        p.velocityMps.roundToInt().toString(),
        p.energyJ.roundToInt().toString(),
        "%.3f".format(p.timeS),
        "%.2f".format(p.mach),
    )
    Row(Modifier.padding(vertical = 4.dp)) {
        cells.forEachIndexed { index, text -> Cell(text, columns[index].second) }
    }
}

private fun side(right: Double) = when {
    right > 0.005 -> "P"
    right < -0.005 -> "L"
    else -> ""
}

@Composable
private fun Cell(text: String, width: Int, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(width.dp).padding(horizontal = 4.dp),
        textAlign = TextAlign.End,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        style = MaterialTheme.typography.bodySmall,
    )
}
