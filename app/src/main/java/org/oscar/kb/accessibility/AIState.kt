package org.oscar.kb.accessibility

import com.mohamedrejeb.richeditor.model.RichTextState

data class AIState(
    val isAIProcessing: Boolean = false,
    val isAICorrecting: Boolean = false,
    val aiText: RichTextState = RichTextState(),
)
