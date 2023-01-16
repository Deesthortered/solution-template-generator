package org.thingsboard.trendz.generator.utils;

import java.util.Random;

public class RandomUtils {

    private static final long seed = 1234567890L;
    private static final Random random = new Random(seed);

    public static Random getRandom() {
        return random;
    }

    public static int getRandomNumber(int from, int to) {
        int i = getRandom().nextInt();
        return from + (i % (to - from));
    }
}
