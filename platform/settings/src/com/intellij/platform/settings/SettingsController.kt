// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus.*
import java.nio.file.Path
import java.util.*

@NonExtendable
interface SettingsController {
  @RequiresBackgroundThread(generateAssertion = false)
  fun <T : Any> getItem(key: SettingDescriptor<T>): T?

  /**
   * The read-write policy cannot be enforced at compile time.
   * The actual controller implementation may rely on runtime rules. Therefore, handle [ReadOnlySettingException] where necessary.
   */
  @Throws(ReadOnlySettingException::class)
  @RequiresBackgroundThread(generateAssertion = false)
  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?)

  @Internal
  @IntellijInternalApi
  fun createStateStorage(collapsedPath: String, file: Path): Any?

  @Internal
  @IntellijInternalApi
  fun release()

  @Internal
  @IntellijInternalApi
  fun <T : Any> doGetItem(key: SettingDescriptor<T>): GetResult<T?>

  @Internal
  @IntellijInternalApi
  fun isPersistenceStateComponentProxy(): Boolean
}

@Internal
@JvmInline
value class GetResult<out T : Any?> @PublishedApi internal constructor(@PublishedApi internal val value: Any?) {
  companion object {
    @Suppress("INAPPLICABLE_JVM_NAME")
    fun <T> resolved(value: T?): GetResult<T> = GetResult(value)

    // partial is supported only for PersistentStateComponent adapter
    fun <T : Any> partial(value: T): GetResult<T> = GetResult(Partial(value))

    fun <T> inapplicable(): GetResult<T> = GetResult(Inapplicable)
  }

  val isResolved: Boolean
    get() = value !is Inapplicable

  val isPartial: Boolean
    get() = value is Partial

  @Suppress("UNCHECKED_CAST")
  fun get(): T? {
    return when (value) {
      is Inapplicable -> null
      is Partial -> value.value as T
      else -> value as T
    }
  }

  override fun toString(): String = if (value is Inapplicable) "Inapplicable" else "Applicable($value)"

  private object Inapplicable
  private class Partial(@JvmField val value: Any)
}

@Internal
enum class SetResult {
  INAPPLICABLE, STOP, FORBID
}

/**
 * Caveat: do not touch telemetry API during init, it is not ready yet.
 */
@Internal
interface DelegatedSettingsController {
  fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?>

  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult

  fun close() {
  }
}

class ReadOnlySettingException(val key: SettingDescriptor<*>) : IllegalStateException()