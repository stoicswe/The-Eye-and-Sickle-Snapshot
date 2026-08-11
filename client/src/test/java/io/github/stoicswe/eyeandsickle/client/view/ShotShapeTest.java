package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠ THE NOSE POINTS WHERE THE SHOT IS AIMING.
 *
 * <p>A triangle is ten pixels across on the field. Whether its point faces the player is not
 * readable from a screenshot — I zoomed one to five times its size and still could not tell a nose
 * from a tail corner. So the geometry is checked as arithmetic, which is the only way this is
 * actually verifiable.
 */
class ShotShapeTest {

    @Test
    @DisplayName("⚠ the first vertex is the nose, and it lies along the heading")
    void theNoseLeads() {
        for (double heading = -Math.PI; heading < Math.PI; heading += 0.3d) {
            double[] p = DefenseGameView.points(0, 0, heading);
            double noseAngle = Math.atan2(p[1], p[0]);

            assertThat(Math.abs(Math.atan2(Math.sin(noseAngle - heading), Math.cos(noseAngle - heading))))
                    .as("heading %.2f: the nose must lie along it", heading)
                    .isLessThan(1e-9d);
        }
    }

    /**
     * ⚠ And it is the FURTHEST point along the heading — a nose that merely lay on the axis but sat
     * behind a tail corner would draw as an arrowhead pointing backwards.
     */
    @Test
    @DisplayName("and it reaches further along the heading than either tail corner")
    void theNoseLeadsTheTails() {
        for (double heading = -Math.PI; heading < Math.PI; heading += 0.3d) {
            double[] p = DefenseGameView.points(0, 0, heading);
            double cos = Math.cos(heading);
            double sin = Math.sin(heading);
            double nose = p[0] * cos + p[1] * sin;
            double tailA = p[2] * cos + p[3] * sin;
            double tailB = p[4] * cos + p[5] * sin;

            assertThat(nose).as("heading %.2f", heading).isGreaterThan(tailA);
            assertThat(nose).as("heading %.2f", heading).isGreaterThan(tailB);
        }
    }

    /** Symmetric about the heading, or the shot reads as bent. */
    @Test
    @DisplayName("the two tail corners sit either side of the heading, equally far off it")
    void theTailIsSymmetric() {
        double[] p = DefenseGameView.points(0, 0, 0.7d);
        double cos = Math.cos(0.7d);
        double sin = Math.sin(0.7d);
        double offA = -p[2] * sin + p[3] * cos;
        double offB = -p[4] * sin + p[5] * cos;

        assertThat(offA).isCloseTo(-offB, org.assertj.core.data.Offset.offset(1e-9d));
        assertThat(Math.abs(offA)).isPositive();
    }
}
