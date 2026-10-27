package pl.ballistics.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.ballistics.app.CalcResult
import pl.ballistics.app.R
import pl.ballistics.app.asString
import pl.ballistics.core.TrajectoryPoint
import kotlin.math.abs
import kotlin.math.roundToInt

private val columns = listOf(
    R.string.col_range to 52, R.string.col_drop to 72, R.string.col_elevation to 68, R.string.col_clicks to 56,
    R.string.col_windage to 72, R.string.col_wind_correction to 68, R.string.col_clicks to 56,
    R.string.col_velocity to 56, R.string.col_energy to 64, R.string.col_time to 56, R.string.col_mach to 52,
)

@Composable
fun TableScreen(result: CalcResult?, modifier: Modifier = Modifier) {
    when (result) {
        null -> Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.padding(32.dp))
        }
        is CalcResult.Error -> Text(
            result.message.asString(),
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
                add(stringResource(R.string.summary_muzzle_velocity, s.muzzleVelocityMps.roundToInt()))
                add(stringResource(R.string.summary_density_altitude, s.densityAltitudeM.roundToInt()))
                s.stabilityFactor?.let { add(stringResource(R.string.summary_stability, it)) }
            }
            Text(summary.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
            Text(velocitySource(result.weapon, result.ammo), style = MaterialTheme.typography.bodySmall)
            s.stabilityFactor?.let {
                if (it < 1.0) Warning(stringResource(R.string.warning_unstable))
                else if (it < 1.4) Warning(stringResource(R.string.warning_marginal))
            }
            Text(
                stringResource(R.string.table_legend, unit.label, result.weapon.clickValue),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // The range column stays put while the other columns scroll sideways under one shared scroll state.
        val sideways = rememberScrollState()
        val (rangeColumn, rest) = columns.first() to columns.drop(1)
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            Cell(stringResource(rangeColumn.first), rangeColumn.second, bold = true)
            Row(Modifier.horizontalScroll(sideways)) {
                rest.forEach { (title, w) -> Cell(stringResource(title), w, bold = true) }
            }
        }
        Row(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
                s.points.forEach { p -> Cell(p.rangeM.roundToInt().toString(), rangeColumn.second, bold = true) }
            }
            Column(Modifier.horizontalScroll(sideways)) {
                s.points.forEach { p -> PointRow(p, unit.radians, click) }
            }
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
    Row {
        cells.forEachIndexed { index, text -> Cell(text, columns[index + 1].second) }
    }
}

private fun side(right: Double) = when {
    right > 0.005 -> "R"
    right < -0.005 -> "L"
    else -> ""
}

private val ROW_HEIGHT = 28.dp

@Composable
private fun Cell(text: String, width: Int, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(width.dp).height(ROW_HEIGHT).padding(horizontal = 4.dp).wrapContentHeight(),
        maxLines = 1,
        textAlign = TextAlign.End,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        style = MaterialTheme.typography.bodySmall,
    )
}
