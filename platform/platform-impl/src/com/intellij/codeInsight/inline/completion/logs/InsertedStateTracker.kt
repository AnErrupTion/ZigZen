// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.INSERTED_STATE_EVENT
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.text.EditDistance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.time.Duration


@ApiStatus.Internal
@Service
class InsertedStateTracker(private val cs: CoroutineScope) {

  fun track(requestId: Long,
            language: Language?,
            fileLanguage: Language?,
            editor: Editor,
            startOffset: Int,
            suggestion: String,
            duration: Duration) {
    ThreadingAssertions.assertReadAccess()
    val rangeMarker = editor.document.createRangeMarker(startOffset, startOffset + suggestion.length)
    cs.launch {
      delay(duration.toMillis())
      if (!editor.isDisposed) {
        val resultText = readAction { if (rangeMarker.isValid) rangeMarker.document.getText(rangeMarker.textRange) else "" }
        rangeMarker.dispose()
        val commonPrefixLength = resultText.commonPrefixWith(suggestion).length
        val commonSuffixLength = resultText.commonSuffixWith(suggestion).length
        val editDistance = EditDistance.optimalAlignment(suggestion, resultText, true)
        val editDistanceNoAdd = editDistance - maxOf(resultText.length - suggestion.length, 0)
        val data = mutableListOf<EventPair<*>>(
          InlineCompletionUsageTracker.InsertedStateEvents.REQUEST_ID.with(requestId),
          InlineCompletionUsageTracker.InsertedStateEvents.DURATION.with(duration.toMillis()),
          InlineCompletionUsageTracker.InsertedStateEvents.SUGGESTION_LENGTH.with(suggestion.length),
          InlineCompletionUsageTracker.InsertedStateEvents.RESULT_LENGTH.with(resultText.length),
          InlineCompletionUsageTracker.InsertedStateEvents.EDIT_DISTANCE.with(editDistance),
          InlineCompletionUsageTracker.InsertedStateEvents.EDIT_DISTANCE_NO_ADD.with(editDistanceNoAdd),
          InlineCompletionUsageTracker.InsertedStateEvents.COMMON_PREFIX_LENGTH.with(commonPrefixLength),
          InlineCompletionUsageTracker.InsertedStateEvents.COMMON_SUFFIX_LENGTH.with(commonSuffixLength),
        )
        language?.let { data.add(EventFields.Language.with(it)) }
        fileLanguage?.let { data.add(EventFields.Language.with(it)) }
        INSERTED_STATE_EVENT.log(data)
      }
    }
  }
}
