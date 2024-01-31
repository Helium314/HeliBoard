/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import helium314.keyboard.latin.utils.Log;

import helium314.keyboard.annotations.UsedForTesting;
import helium314.keyboard.keyboard.internal.MatrixUtils.MatrixOperationFailedException;

import java.util.Arrays;

/**
 * Utilities to smooth coordinates. Currently, we calculate 3d least squares formula by using
 * Lagrangian smoothing
 */
@UsedForTesting
public class SmoothingUtils {
    private static final String TAG = SmoothingUtils.class.getSimpleName();
    private static final boolean DEBUG = false;

    private SmoothingUtils() {
        // not allowed to instantiate publicly
    }

    /**
     * Find a most likely 3d least squares formula for specified coordinates.
     * "retval" should be a 1x4 size matrix.
     */
    @UsedForTesting
    public static void get3DParameters(final float[] xs, final float[] ys,
            final float[][] retval) throws MatrixOperationFailedException {
        final int COEFF_COUNT = 4; // Coefficient count for 3d smoothing
        if (retval.length != COEFF_COUNT || retval[0].length != 1) {
            Log.d(TAG, "--- invalid length of 3d retval " + retval.length + ", "
                    + retval[0].length);
            return;
        }
        final int N = xs.length;
        // TODO: Never isntantiate the matrix
        final float[][] m0 = new float[COEFF_COUNT][COEFF_COUNT];
        final float[][] m0Inv = new float[COEFF_COUNT][COEFF_COUNT];
        final float[][] m1 = new float[COEFF_COUNT][N];
        final float[][] m2 = new float[N][1];

        // m0
        for (int i = 0; i < COEFF_COUNT; ++i) {
            Arrays.fill(m0[i], 0);
            for (int j = 0; j < COEFF_COUNT; ++j) {
                final int pow = i + j;
                for (float x : xs) {
                    m0[i][j] += (float) Math.pow(x, pow);
                }
            }
        }
        // m0Inv
        MatrixUtils.inverse(m0, m0Inv);
        if (DEBUG) {
            MatrixUtils.dump("m0-1", m0Inv);
        }

        // m1
        for (int i = 0; i < COEFF_COUNT; ++i) {
            for (int j = 0; j < N; ++j) {
                m1[i][j] = (i == 0) ? 1.0f : m1[i - 1][j] * xs[j];
            }
        }

        // m2
        for (int i = 0; i < N; ++i) {
            m2[i][0] = ys[i];
        }

        final float[][] m0Invxm1 = new float[COEFF_COUNT][N];
        if (DEBUG) {
            MatrixUtils.dump("a0", m0Inv);
            MatrixUtils.dump("a1", m1);
        }
        MatrixUtils.multiply(m0Inv, m1, m0Invxm1);
        if (DEBUG) {
            MatrixUtils.dump("a2", m0Invxm1);
            MatrixUtils.dump("a3", m2);
        }
        MatrixUtils.multiply(m0Invxm1, m2, retval);
        if (DEBUG) {
            MatrixUtils.dump("result", retval);
        }
    }
}
