package org.dslul.openboard.inputmethod.latin.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoOutput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoOutput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess


@TaskerInputRoot
data class TaskerText @JvmOverloads constructor(@field:TaskerInputField("text") var text: String = "")

class SendTextAction : Activity(), TaskerPluginConfig<TaskerText> {

    var text: String = ""
    lateinit var editText: EditText
    private val helper by lazy { SendTextActionHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editText = EditText(this)
        // simple edit text ui
        val a = AlertDialog.Builder(this)
            .setTitle("Send Text")
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                text = editText.text.toString()
                helper.finishForTasker()
            }
            .setNegativeButton("Cancel") { _, _ ->
                helper.onBackPressed()
            }.create()
        editText.requestFocus()
        a.show()


        helper.onCreate()
    }

    override val context: Context
        get() = applicationContext
    override val inputForTasker: TaskerInput<TaskerText>
        get() {
            return TaskerInput(TaskerText(text))
        }

    override fun assignFromInput(input: TaskerInput<TaskerText>) {
        text = input.regular.text
    }
}

class SendTextActionHelper(config: TaskerPluginConfig<TaskerText>) :
    TaskerPluginConfigHelperNoOutput<TaskerText, SendTextActionRunner>(config) {
    override val inputClass: Class<TaskerText> = TaskerText::class.java
    override val runnerClass: Class<SendTextActionRunner> = SendTextActionRunner::class.java
}

class SendTextActionRunner : TaskerPluginRunnerActionNoOutput<TaskerText>() {
    override fun run(context: Context, input: TaskerInput<TaskerText>): TaskerPluginResult<Unit> =
        GlobalKeyboardListener.listener?.onTextInput(input.regular.text)
            ?.let { TaskerPluginResultSucess() } ?: TaskerPluginResultSucess()
}
