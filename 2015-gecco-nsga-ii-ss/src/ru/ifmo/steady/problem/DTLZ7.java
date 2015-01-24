package ru.ifmo.steady.problem;

import ru.ifmo.steady.Problem;
import ru.ifmo.steady.Solution;

public class DTLZ7 implements Problem {
    private double g(double[] input, int first) {
        int last = input.length;
        double sum = 0;
        double sumSq = 0;
        for (int i = first; i < last; ++i) {
            sum += input[i];
            sumSq += input[i] * input[i];
        }
        return 1 + 9 / Math.sqrt(sumSq) * sum;
    }

    public int inputDimension() {
        return 21;
    }

    public Solution evaluate(double[] input) {
        double gm = g(input, 1);
        double h = 2 - input[0] / (1 + gm) * (1 + Math.sin(3 * Math.PI * input[0]));
        return new Solution(
            input[0],
            (1 + gm) * h,
            input
        );
    }
}
