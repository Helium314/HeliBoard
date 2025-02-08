package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorEnvelope
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

// todo:
//  setting from text doesn't work
//  weird effect on start, did this start with the top row showing colors?
//  text field doesn't look nice
//  for initial color picks performance is not good
@Composable
fun ColorPickerDialog(
    onDismissRequest: () -> Unit,
    initialColor: Int,
    title: String,
    onConfirmed: (Int) -> Unit,
) {
    val controller = rememberColorPickerController()
    val barHeight = 35.dp
    var value by remember { mutableStateOf(TextFieldValue(initialColor.toString(16))) }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { onConfirmed(controller.selectedColor.value.toArgb()) },
        title = { Text(title) },
        text = {
            Column {
                Row {
                    Surface(
                        color = Color(initialColor),
                        modifier = Modifier.fillMaxWidth(0.5f)
                            .padding(start = 10.dp)
                            .height(barHeight))
                    {  }
                    Surface(
                        color = controller.selectedColor.value,
                        modifier = Modifier.fillMaxWidth()
                            .padding(end = 10.dp)
                            .height(barHeight))
                    {  }
                }
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .padding(10.dp),
                    controller = controller,
                    onColorChanged = { colorEnvelope: ColorEnvelope ->
                        value = TextFieldValue(colorEnvelope.hexCode)
                    },
                    initialColor = Color(initialColor)
                )
                AlphaSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .height(barHeight),
                    controller = controller,
                )
                BrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        .height(barHeight),
                    controller = controller,
                )
                TextField(
                    value = value,
                    onValueChange = {
                        val androidColor = kotlin.runCatching { android.graphics.Color.parseColor("#$it") }.getOrNull()
                        if (androidColor != null)
                            controller.selectByColor(Color(androidColor), true)
                    }
                )
            }
        }
    )
}

@Preview
@Composable
private fun Preview() {
    ColorPickerDialog({}, android.graphics.Color.MAGENTA, "color name", {})
}