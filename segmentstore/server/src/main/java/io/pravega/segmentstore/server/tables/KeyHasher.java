/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.segmentstore.server.tables;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.pravega.common.util.ArrayView;
import io.pravega.common.util.BitConverter;
import io.pravega.common.util.ByteArraySegment;
import java.util.UUID;
import java.util.function.Function;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Defines a Hasher for a Table Key.
 */
public abstract class KeyHasher {
    static final int HASH_SIZE_BYTES = Long.BYTES + Long.BYTES; // UUID length.

    /**
     * Generates a new Key Hash for the given Key.
     *
     * @param key The Key to hash.
     * @return A UUID representing the Hash for the given Key.
     */
    public UUID hash(@NonNull byte[] key) {
        return hash(new ByteArraySegment(key));
    }

    /**
     * Generates a new Key Hash for the given Key.
     *
     * @param key The Key to hash.
     * @return A UUID representing the Hash for the given Key.
     */
    public abstract UUID hash(@NonNull ArrayView key);

    protected UUID toUUID(byte[] rawHash) {
        assert rawHash.length == HASH_SIZE_BYTES;
        long msb = BitConverter.readLong(rawHash, 0);
        long lsb = BitConverter.readLong(rawHash, Long.BYTES);
        if (msb == TableBucket.CORE_ATTRIBUTE_PREFIX) {
            msb++;
        } else if (msb == TableBucket.BACKPOINTER_PREFIX) {
            msb--;
        }

        return new UUID(msb, lsb);
    }

    /**
     * Creates a new instance of the KeyHasher class that generates hashes using the SHA-256 algorithm.
     *
     * @return A new instance of the KeyHasher class.
     */
    public static KeyHasher sha256() {
        return new Sha256Hasher();
    }

    /**
     * Creates a new instance of the KeyHasher class that generates custom hashes, based on the given Function.
     *
     * @param hashFunction A Function that, given an {@link ArrayView}, produces a byte array representing its hash.
     * @return A new instance of the KeyHasher class.
     */
    @VisibleForTesting
    public static KeyHasher custom(Function<ArrayView, byte[]> hashFunction) {
        return new CustomHasher(hashFunction);
    }

    //region Sha256Hasher

    private static class Sha256Hasher extends KeyHasher {
        private final HashFunction hash = Hashing.sha256();

        @Override
        public UUID hash(@NonNull ArrayView key) {
            byte[] rawHash = new byte[HASH_SIZE_BYTES];
            int c = this.hash.hashBytes(key.array(), key.arrayOffset(), key.getLength()).writeBytesTo(rawHash, 0, rawHash.length);
            assert c == rawHash.length;
            return toUUID(rawHash);
        }
    }

    //endregion

    //region CustomHasher

    @RequiredArgsConstructor
    private static class CustomHasher extends KeyHasher {
        @NonNull
        private final Function<ArrayView, byte[]> hashFunction;

        @Override
        public UUID hash(@NonNull ArrayView key) {
            byte[] rawHash = this.hashFunction.apply(key);
            Preconditions.checkState(rawHash.length == HASH_SIZE_BYTES, "Resulting KeyHash has incorrect length.");
            return toUUID(rawHash);
        }
    }

    //endregion
}