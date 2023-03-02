package org.thingsboard.trendz.generator.utils;

import java.util.Random;
import java.util.UUID;

public class RandomUtils {

    private static final long seed = 1234567890L;
    private static Random random = new Random(seed);


    public static void refreshRandom() {
        random = new Random(seed);
    }
    public static Random getRandom() {
        return random;
    }

    public static boolean getBooleanByProbability(double probability) {
        if (probability < 0 || 1 < probability) {
            throw new IllegalArgumentException(
                    String.format("Probablity must be in range [0, 1], given = %s", probability)
            );
        }
        return random.nextDouble() < probability;
    }

    public static double getRandomNumber(double from, double to) {
        if (to < from) {
            throw new IllegalArgumentException(String.format("'From' value is bigger than 'to' (%s, %s).", from, to));
        }
        if (from == to) {
            return to;
        }
        double i = Math.abs(getRandom().nextDouble());
        return from + (i * (to - from));
    }

    public static UUID getRandonUUID() {
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);
        return UUID.nameUUIDFromBytes(randomBytes);
    }
}
