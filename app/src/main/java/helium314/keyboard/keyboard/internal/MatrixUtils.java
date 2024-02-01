/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import helium314.keyboard.latin.utils.Log;

import java.util.Arrays;
import java.util.Locale;

/**
 * Utilities for matrix operations. Don't instantiate objects inside this class to prevent
 * unexpected performance regressions.
 */
public class MatrixUtils {
    static final String TAG = MatrixUtils.class.getSimpleName();

    public static class MatrixOperationFailedException extends Exception {
        private static final long serialVersionUID = 4384485606788583829L;

        public MatrixOperationFailedException(String msg) {
            super(msg);
            Log.d(TAG, msg);
        }
    }

    /**
     * A utility function to inverse matrix.
     * Find a pivot and swap the row of squareMatrix0 and squareMatrix1
     */
    private static void findPivotAndSwapRow(final int row, final float[][] squareMatrix0,
            final float[][] squareMatrix1, final int size) {
        int ip = row;
        float pivot = Math.abs(squareMatrix0[row][row]);
        for (int i = row + 1; i < size; ++i) {
            if (pivot < Math.abs(squareMatrix0[i][row])) {
                ip = i;
                pivot = Math.abs(squareMatrix0[i][row]);
            }
        }
        if (ip != row) {
            for (int j = 0; j < size; ++j) {
                final float temp0 = squareMatrix0[ip][j];
                squareMatrix0[ip][j] = squareMatrix0[row][j];
                squareMatrix0[row][j] = temp0;
                final float temp1 = squareMatrix1[ip][j];
                squareMatrix1[ip][j] = squareMatrix1[row][j];
                squareMatrix1[row][j] = temp1;
            }
        }
    }

    /**
     * A utility function to inverse matrix. This function calculates answer for each row by
     * sweeping method of Gauss Jordan elimination
     */
    private static void sweep(final int row, final float[][] squareMatrix0,
            final float[][] squareMatrix1, final int size) throws MatrixOperationFailedException {
        final float pivot = squareMatrix0[row][row];
        if (pivot == 0) {
            throw new MatrixOperationFailedException("Inverse failed. Invalid pivot");
        }
        for (int j = 0; j < size; ++j) {
            squareMatrix0[row][j] /= pivot;
            squareMatrix1[row][j] /= pivot;
        }
        for (int i = 0; i < size; i++) {
            final float sweepTargetValue = squareMatrix0[i][row];
            if (i != row) {
                for (int j = row; j < size; ++j) {
                    squareMatrix0[i][j] -= sweepTargetValue * squareMatrix0[row][j];
                }
                for (int j = 0; j < size; ++j) {
                    squareMatrix1[i][j] -= sweepTargetValue * squareMatrix1[row][j];
                }
            }
        }
    }

    /**
     * A function to inverse matrix.
     * The inverse matrix of squareMatrix will be output to inverseMatrix. Please notice that
     * the value of squareMatrix is modified in this function and can't be resuable.
     */
    public static void inverse(final float[][] squareMatrix,
            final float[][] inverseMatrix) throws MatrixOperationFailedException {
        final int size = squareMatrix.length;
        if (squareMatrix[0].length != size || inverseMatrix.length != size
                || inverseMatrix[0].length != size) {
            throw new MatrixOperationFailedException(
                    "--- invalid length. column should be 2 times larger than row.");
        }
        for (int i = 0; i < size; ++i) {
            Arrays.fill(inverseMatrix[i], 0.0f);
            inverseMatrix[i][i] = 1.0f;
        }
        for (int i = 0; i < size; ++i) {
            findPivotAndSwapRow(i, squareMatrix, inverseMatrix, size);
            sweep(i, squareMatrix, inverseMatrix, size);
        }
    }

    /**
     * A matrix operation to multiply m0 and m1.
     */
    public static void multiply(final float[][] m0, final float[][] m1,
            final float[][] retval) throws MatrixOperationFailedException {
        if (m0[0].length != m1.length) {
            throw new MatrixOperationFailedException(
                    "--- invalid length for multiply " + m0[0].length + ", " + m1.length);
        }
        final int m0h = m0.length;
        final int m0w = m0[0].length;
        final int m1w = m1[0].length;
        if (retval.length != m0h || retval[0].length != m1w) {
            throw new MatrixOperationFailedException(
                    "--- invalid length of retval " + retval.length + ", " + retval[0].length);
        }

        for (int i = 0; i < m0h; i++) {
            Arrays.fill(retval[i], 0);
            for (int j = 0; j < m1w; j++) {
                for (int k = 0; k < m0w; k++) {
                    retval[i][j] += m0[i][k] * m1[k][j];
                }
            }
        }
    }

    /**
     * A utility function to dump the specified matrix in a readable way
     */
    public static void dump(final String title, final float[][] a) {
        final int column = a[0].length;
        final int row = a.length;
        Log.d(TAG, "Dump matrix: " + title);
        Log.d(TAG, "/*---------------------");
        final StringBuilder sb = new StringBuilder();
        for (float[] floats : a) {
            sb.setLength(0);
            for (int j = 0; j < column; ++j) {
                sb.append(String.format(Locale.ROOT, "%4f", floats[j])).append(' ');
            }
            Log.d(TAG, sb.toString());
        }
        Log.d(TAG, "---------------------*/");
    }
}
