// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@Composable
fun ColorPickerDialog(
    onDismissRequest: () -> Unit,
    initialColor: Int,
    title: String,
    onConfirmed: (Int) -> Unit,
) {
    val controller = rememberColorPickerController()
    val wheelPaint = Paint().apply {
        alpha = 0.5f
        style = PaintingStyle.Stroke
        strokeWidth = 5f
        color = Color.White
    }
    controller.wheelPaint = wheelPaint
    val barHeight = 35.dp
    val initialString = initialColor.toUInt().toString(16)
    var textValue by remember { mutableStateOf(TextFieldValue(initialString, TextRange(initialString.length))) }
    var currentColor by remember { mutableStateOf(Color(initialColor)) }
    val width = LocalConfiguration.current.screenWidthDp
    val height = LocalConfiguration.current.screenHeightDp
    val useWideLayout = height < 500 && width > height
    @Composable fun topBar() {
        Row {
            Surface(
                color = Color(initialColor),
                modifier = Modifier.fillMaxWidth(0.5f)
                    .padding(start = 10.dp)
                    .height(barHeight))
            {  }
            Surface(
                color = currentColor,
                modifier = Modifier.fillMaxWidth()
                    .padding(end = 10.dp)
                    .height(barHeight))
            {  }
        }
    }
    @Composable fun picker() {
        HsvColorPicker(
            modifier = Modifier
                .size(300.dp)
                .padding(10.dp),
            controller = controller,
            onColorChanged = {
                if (it.fromUser)
                    textValue = TextFieldValue(it.hexCode, selection = TextRange(it.hexCode.length))
                currentColor = it.color
            },
            initialColor = Color(initialColor),
        )
    }
    @Composable fun slidersAndTextField() {
        AlphaSlider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .height(barHeight),
            controller = controller,
            initialColor = Color(initialColor),
            wheelPaint = wheelPaint
        )
        BrightnessSlider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .height(barHeight),
            controller = controller,
            initialColor = Color(initialColor),
            wheelPaint = wheelPaint
        )
        TextField(
            value = textValue,
            // todo: KeyboardType.Password is a crappy way of avoiding suggestions... is there really no way in compose?
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            onValueChange = {
                textValue = it
                val androidColor = runCatching { android.graphics.Color.parseColor("#${it.text}") }.getOrNull()
                if (androidColor != null)
                    controller.selectByColor(Color(androidColor), false)
            }
        )
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(controller.selectedColor.value.toArgb()) },
        title = { Text(title) },
        content = {
            if (useWideLayout)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    picker()
                    Column {
                        topBar()
                        slidersAndTextField()
                    }
                }
            else
                Column {
                    topBar()
                    picker()
                    slidersAndTextField()
                }
        },
        properties = DialogProperties(usePlatformDefaultWidth = !useWideLayout)
    )
}

@Preview
@Composable
private fun Preview() {
    ColorPickerDialog({}, -0x0f4488aa, "color name", {})
}
