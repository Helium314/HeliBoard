/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

/**
 * Interpolates XY-coordinates using Cubic Hermite Curve.
 */
public final class HermiteInterpolator {
    private int[] mXCoords;
    private int[] mYCoords;
    private int mMinPos;
    private int mMaxPos;

    // Working variable to calculate interpolated value.
    /** The coordinates of the start point of the interval. */
    public int mP1X, mP1Y;
    /** The coordinates of the end point of the interval. */
    public int mP2X, mP2Y;
    /** The slope of the tangent at the start point. */
    public float mSlope1X, mSlope1Y;
    /** The slope of the tangent at the end point. */
    public float mSlope2X, mSlope2Y;
    /** The interpolated coordinates.
     * The return variables of {@link #interpolate(float)} to avoid instantiations.
     */
    public float mInterpolatedX, mInterpolatedY;

    public HermiteInterpolator() {
        // Nothing to do with here.
    }

    /**
     * Reset this interpolator to point XY-coordinates data.
     * @param xCoords the array of x-coordinates. Valid data are in left-open interval
     *                <code>[minPos, maxPos)</code>.
     * @param yCoords the array of y-coordinates. Valid data are in left-open interval
     *                <code>[minPos, maxPos)</code>.
     * @param minPos the minimum index of left-open interval of valid data.
     * @param maxPos the maximum index of left-open interval of valid data.
     */
    public void reset(final int[] xCoords, final int[] yCoords, final int minPos,
            final int maxPos) {
        mXCoords = xCoords;
        mYCoords = yCoords;
        mMinPos = minPos;
        mMaxPos = maxPos;
    }

    /**
     * Set interpolation interval.
     * <p>
     * The start and end coordinates of the interval will be set in {@link #mP1X}, {@link #mP1Y},
     * {@link #mP2X}, and {@link #mP2Y}. The slope of the tangents at start and end points will be
     * set in {@link #mSlope1X}, {@link #mSlope1Y}, {@link #mSlope2X}, and {@link #mSlope2Y}.
     *
     * @param p0 the index just before interpolation interval. If <code>p1</code> points the start
     *           of valid points, <code>p0</code> must be less than <code>minPos</code> of
     *           {@link #reset(int[],int[],int,int)}.
     * @param p1 the start index of interpolation interval.
     * @param p2 the end index of interpolation interval.
     * @param p3 the index just after interpolation interval. If <code>p2</code> points the end of
     *           valid points, <code>p3</code> must be equal or greater than <code>maxPos</code> of
     *           {@link #reset(int[],int[],int,int)}.
     */
    public void setInterval(final int p0, final int p1, final int p2, final int p3) {
        mP1X = mXCoords[p1];
        mP1Y = mYCoords[p1];
        mP2X = mXCoords[p2];
        mP2Y = mYCoords[p2];
        // A(ax,ay) is the vector p1->p2.
        final int ax = mP2X - mP1X;
        final int ay = mP2Y - mP1Y;

        // Calculate the slope of the tangent at p1.
        if (p0 >= mMinPos) {
            // p1 has previous valid point p0.
            // The slope of the tangent is half of the vector p0->p2.
            mSlope1X = (mP2X - mXCoords[p0]) / 2.0f;
            mSlope1Y = (mP2Y - mYCoords[p0]) / 2.0f;
        } else if (p3 < mMaxPos) {
            // p1 has no previous valid point, but p2 has next valid point p3.
            // B(bx,by) is the slope vector of the tangent at p2.
            final float bx = (mXCoords[p3] - mP1X) / 2.0f;
            final float by = (mYCoords[p3] - mP1Y) / 2.0f;
            final float crossProdAB = ax * by - ay * bx;
            final float dotProdAB = ax * bx + ay * by;
            final float normASquare = ax * ax + ay * ay;
            final float invHalfNormASquare = 1.0f / normASquare / 2.0f;
            // The slope of the tangent is the mirror image of vector B to vector A.
            mSlope1X = invHalfNormASquare * (dotProdAB * ax + crossProdAB * ay);
            mSlope1Y = invHalfNormASquare * (dotProdAB * ay - crossProdAB * ax);
        } else {
            // p1 and p2 have no previous valid point. (Interval has only point p1 and p2)
            mSlope1X = ax;
            mSlope1Y = ay;
        }

        // Calculate the slope of the tangent at p2.
        if (p3 < mMaxPos) {
            // p2 has next valid point p3.
            // The slope of the tangent is half of the vector p1->p3.
            mSlope2X = (mXCoords[p3] - mP1X) / 2.0f;
            mSlope2Y = (mYCoords[p3] - mP1Y) / 2.0f;
        } else if (p0 >= mMinPos) {
            // p2 has no next valid point, but p1 has previous valid point p0.
            // B(bx,by) is the slope vector of the tangent at p1.
            final float bx = (mP2X - mXCoords[p0]) / 2.0f;
            final float by = (mP2Y - mYCoords[p0]) / 2.0f;
            final float crossProdAB = ax * by - ay * bx;
            final float dotProdAB = ax * bx + ay * by;
            final float normASquare = ax * ax + ay * ay;
            final float invHalfNormASquare = 1.0f / normASquare / 2.0f;
            // The slope of the tangent is the mirror image of vector B to vector A.
            mSlope2X = invHalfNormASquare * (dotProdAB * ax + crossProdAB * ay);
            mSlope2Y = invHalfNormASquare * (dotProdAB * ay - crossProdAB * ax);
        } else {
            // p1 and p2 has no previous valid point. (Interval has only point p1 and p2)
            mSlope2X = ax;
            mSlope2Y = ay;
        }
    }

    /**
     * Calculate interpolation value at <code>t</code> in unit interval <code>[0,1]</code>.
     * <p>
     * On the unit interval [0,1], given a starting point p1 at t=0 and an ending point p2 at t=1
     * with the slope of the tangent m1 at p1 and m2 at p2, the polynomial of cubic Hermite curve
     * can be defined by
     *   p(t) = (1+2t)(1-t)(1-t)*p1 + t(1-t)(1-t)*m1 + (3-2t)t^2*p2 + (t-1)t^2*m2
     * where t is an element of [0,1].
     * <p>
     * The interpolated XY-coordinates will be set in {@link #mInterpolatedX} and
     * {@link #mInterpolatedY}.
     *
     * @param t the interpolation parameter. The value must be in close interval <code>[0,1]</code>.
     */
    public void interpolate(final float t) {
        final float omt = 1.0f - t;
        final float tm2 = 2.0f * t;
        final float k1 = 1.0f + tm2;
        final float k2 = 3.0f - tm2;
        final float omt2 = omt * omt;
        final float t2 = t * t;
        mInterpolatedX = (k1 * mP1X + t * mSlope1X) * omt2 + (k2 * mP2X - omt * mSlope2X) * t2;
        mInterpolatedY = (k1 * mP1Y + t * mSlope1Y) * omt2 + (k2 * mP2Y - omt * mSlope2Y) * t2;
    }
}
