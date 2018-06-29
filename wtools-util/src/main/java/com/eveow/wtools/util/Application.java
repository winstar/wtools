package com.eveow.wtools.util;

/**
 * @author wangjianping
 */
public class Application {

    public static void main(String[] args) {
        LinearCongruentialGenerator generator = new LinearCongruentialGenerator(5);

        int next = 0;
        for (int i = 0; i < 100; i++) {
            next = (int) ((0 + 1) / generator.nextDouble());
            System.out.println(next);
        }
    }

    private static final class LinearCongruentialGenerator {
        private long state;

        public LinearCongruentialGenerator(long seed) {
            this.state = seed;
        }

        public double nextDouble() {
            state = 2862933555777941757L * state + 1;
            return ((double) ((int) (state >>> 33) + 1)) / (0x1.0p31);
        }
    }
}
