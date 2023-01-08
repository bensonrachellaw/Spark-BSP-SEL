package cn.edu.szu.bigdata;

import smile.math.MathEx;

/**
 * Created R2 by ZYM ;
 */

public class R2 {
    private static final long serialVersionUID = 2L;
    public static final R2 instance = new R2();

    public R2() {
    }

    public double score(double[] truth, double[] prediction) {
        return of(truth, prediction);
    }

    public static double of(double[] truth, double[] prediction) {
        if (truth.length != prediction.length) {
            throw new IllegalArgumentException(String.format("The vector sizes don't match: %d != %d.", truth.length, prediction.length));
        } else {
            double RSS = 0.0D;
            double TSS = 0.0D;
            double ybar = MathEx.mean(truth);
            int n = truth.length;

            for(int i = 0; i < n; ++i) {
                double r = truth[i] - prediction[i];
                RSS += r * r;
                double t = truth[i] - ybar;
                TSS += t * t;
            }

            return 1.0D - RSS / TSS;
        }
    }

    public String toString() {
        return "R2";
    }
}
