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

    public static long getRandomNumber(long from, long to) {
        if (from == to) {
            return to;
        }
        long i = Math.abs(getRandom().nextLong());
        return from + (i % (to - from));
    }

    public static UUID getRandonUUID() {
        byte[] randomBytes = new byte[16];
        random.nextBytes(randomBytes);
        return UUID.nameUUIDFromBytes(randomBytes);
    }
}
