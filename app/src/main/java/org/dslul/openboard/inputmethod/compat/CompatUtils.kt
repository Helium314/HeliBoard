/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import android.text.TextUtils
import android.util.Log
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object CompatUtils {
    private val TAG = CompatUtils::class.java.simpleName
    fun getClass(className: String?): Class<*>? {
        return try {
            Class.forName(className!!)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    fun getMethod(targetClass: Class<*>?, name: String?,
                  vararg parameterTypes: Class<*>?): Method? {
        if (targetClass == null || TextUtils.isEmpty(name)) {
            return null
        }
        try {
            return targetClass.getMethod(name!!, *parameterTypes)
        } catch (_: SecurityException) { // ignore
        } catch (_: NoSuchMethodException) {
        }
        return null
    }

    fun getField(targetClass: Class<*>?, name: String?): Field? {
        if (targetClass == null || TextUtils.isEmpty(name)) {
            return null
        }
        try {
            return targetClass.getField(name!!)
        } catch (_: SecurityException) { // ignore
        } catch (_: NoSuchFieldException) {
        }
        return null
    }

    fun getConstructor(targetClass: Class<*>?,
                       vararg types: Class<*>?): Constructor<*>? {
        if (targetClass == null || types == null) {
            return null
        }
        try {
            return targetClass.getConstructor(*types)
        } catch (_: SecurityException) { // ignore
        } catch (_: NoSuchMethodException) {
        }
        return null
    }

    fun newInstance(constructor: Constructor<*>?, vararg args: Any?): Any? {
        if (constructor == null) {
            return null
        }
        try {
            return constructor.newInstance(*args)
        } catch (e: InstantiationException) {
            Log.e(TAG, "Exception in newInstance", e)
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "Exception in newInstance", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception in newInstance", e)
        } catch (e: InvocationTargetException) {
            Log.e(TAG, "Exception in newInstance", e)
        }
        return null
    }

    operator fun invoke(receiver: Any?, defaultValue: Any?,
                        method: Method?, vararg args: Any?): Any? {
        if (method == null) {
            return defaultValue
        }
        try {
            return method.invoke(receiver, *args)
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "Exception in invoke", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception in invoke", e)
        } catch (e: InvocationTargetException) {
            Log.e(TAG, "Exception in invoke", e)
        }
        return defaultValue
    }

    fun getFieldValue(receiver: Any?, defaultValue: Any?,
                      field: Field?): Any? {
        if (field == null) {
            return defaultValue
        }
        try {
            return field[receiver]
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "Exception in getFieldValue", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception in getFieldValue", e)
        }
        return defaultValue
    }

    fun setFieldValue(receiver: Any?, field: Field?, value: Any?) {
        if (field == null) {
            return
        }
        try {
            field[receiver] = value
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "Exception in setFieldValue", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Exception in setFieldValue", e)
        }
    }

    fun getClassWrapper(className: String?): ClassWrapper {
        return ClassWrapper(getClass(className))
    }

    class ClassWrapper(private val mClass: Class<*>?) {
        fun exists(): Boolean {
            return mClass != null
        }

        fun <T> getMethod(name: String?,
                          defaultValue: T, vararg parameterTypes: Class<*>?): ToObjectMethodWrapper<T> {
            return ToObjectMethodWrapper(getMethod(mClass, name, *parameterTypes),
                    defaultValue)
        }

        fun getPrimitiveMethod(name: String?, defaultValue: Int,
                               vararg parameterTypes: Class<*>?): ToIntMethodWrapper {
            return ToIntMethodWrapper(getMethod(mClass, name, *parameterTypes),
                    defaultValue)
        }

        fun getPrimitiveMethod(name: String?, defaultValue: Float,
                               vararg parameterTypes: Class<*>?): ToFloatMethodWrapper {
            return ToFloatMethodWrapper(getMethod(mClass, name, *parameterTypes),
                    defaultValue)
        }

        fun getPrimitiveMethod(name: String?,
                               defaultValue: Boolean, vararg parameterTypes: Class<*>?): ToBooleanMethodWrapper {
            return ToBooleanMethodWrapper(getMethod(mClass, name, *parameterTypes),
                    defaultValue)
        }

    }

    @Suppress("unchecked_cast")
    class ToObjectMethodWrapper<T>(private val mMethod: Method?, private val mDefaultValue: T) {
        operator fun invoke(receiver: Any?, vararg args: Any?): T {
            return CompatUtils.invoke(receiver, mDefaultValue, mMethod, *args) as T
        }

    }

    class ToIntMethodWrapper(private val mMethod: Method?, private val mDefaultValue: Int) {
        operator fun invoke(receiver: Any?, vararg args: Any?): Int {
            return CompatUtils.invoke(receiver, mDefaultValue, mMethod, *args) as Int
        }

    }

    class ToFloatMethodWrapper(private val mMethod: Method?, private val mDefaultValue: Float) {
        operator fun invoke(receiver: Any?, vararg args: Any?): Float {
            return CompatUtils.invoke(receiver, mDefaultValue, mMethod, *args) as Float
        }

    }

    class ToBooleanMethodWrapper(private val mMethod: Method?, private val mDefaultValue: Boolean) {
        operator fun invoke(receiver: Any?, vararg args: Any?): Boolean {
            return CompatUtils.invoke(receiver, mDefaultValue, mMethod, *args) as Boolean
        }

    }
}