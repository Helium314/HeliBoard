/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

public final class RoundedLine {
    private final RectF mArc1 = new RectF();
    private final RectF mArc2 = new RectF();
    private final Path mPath = new Path();

    private static final double RADIAN_TO_DEGREE = 180.0d / Math.PI;
    private static final double RIGHT_ANGLE = Math.PI / 2.0d;

    /**
     * Make a rounded line path
     *
     * @param p1x the x-coordinate of the start point.
     * @param p1y the y-coordinate of the start point.
     * @param r1 the radius at the start point
     * @param p2x the x-coordinate of the end point.
     * @param p2y the y-coordinate of the end point.
     * @param r2 the radius at the end point
     * @return an instance of {@link Path} that holds the result rounded line, or an instance of
     * {@link Path} that holds an empty path if the start and end points are equal.
     */
    public Path makePath(final float p1x, final float p1y, final float r1,
            final float p2x, final float p2y, final float r2) {
        mPath.rewind();
        final double dx = p2x - p1x;
        final double dy = p2y - p1y;
        // Distance of the points.
        final double l = Math.hypot(dx, dy);
        if (Double.compare(0.0d, l) == 0) {
            return mPath; // Return an empty path
        }
        // Angle of the line p1-p2
        final double a = Math.atan2(dy, dx);
        // Difference of trail cap radius.
        final double dr = r2 - r1;
        // Variation of angle at trail cap.
        final double ar = Math.asin(dr / l);
        // The start angle of trail cap arc at P1.
        final double aa = a - (RIGHT_ANGLE + ar);
        // The end angle of trail cap arc at P2.
        final double ab = a + (RIGHT_ANGLE + ar);
        final float cosa = (float)Math.cos(aa);
        final float sina = (float)Math.sin(aa);
        final float cosb = (float)Math.cos(ab);
        final float sinb = (float)Math.sin(ab);
        // Closing point of arc at P1.
        final float p1ax = p1x + r1 * cosa;
        final float p1ay = p1y + r1 * sina;
        // Opening point of arc at P1.
        final float p1bx = p1x + r1 * cosb;
        final float p1by = p1y + r1 * sinb;
        // Opening point of arc at P2.
        final float p2ax = p2x + r2 * cosa;
        final float p2ay = p2y + r2 * sina;
        // Closing point of arc at P2.
        final float p2bx = p2x + r2 * cosb;
        final float p2by = p2y + r2 * sinb;
        // Start angle of the trail arcs.
        final float angle = (float)(aa * RADIAN_TO_DEGREE);
        final float ar2degree = (float)(ar * 2.0d * RADIAN_TO_DEGREE);
        // Sweep angle of the trail arc at P1.
        final float a1 = -180.0f + ar2degree;
        // Sweep angle of the trail arc at P2.
        final float a2 = 180.0f + ar2degree;
        mArc1.set(p1x, p1y, p1x, p1y);
        mArc1.inset(-r1, -r1);
        mArc2.set(p2x, p2y, p2x, p2y);
        mArc2.inset(-r2, -r2);

        // Trail cap at P1.
        mPath.moveTo(p1x, p1y);
        mPath.arcTo(mArc1, angle, a1);
        // Trail cap at P2.
        mPath.moveTo(p2x, p2y);
        mPath.arcTo(mArc2, angle, a2);
        // Two trapezoids connecting P1 and P2.
        mPath.moveTo(p1ax, p1ay);
        mPath.lineTo(p1x, p1y);
        mPath.lineTo(p1bx, p1by);
        mPath.lineTo(p2bx, p2by);
        mPath.lineTo(p2x, p2y);
        mPath.lineTo(p2ax, p2ay);
        mPath.close();
        return mPath;
    }

    public void getBounds(final Rect outBounds) {
        // Reuse mArc1 as working variable
        mPath.computeBounds(mArc1, true /* unused */);
        mArc1.roundOut(outBounds);
    }
}
