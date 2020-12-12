/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package threads.thor.bt.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import threads.thor.bt.BtException;

/**
 * This utility class provides cryptographic functions.
 *
 * @since 1.0
 */
public class CryptoUtil {

    /**
     * Calculate SHA-1 digest of a byte array.
     *
     * @since 1.0
     */
    public static byte[] getSha1Digest(byte[] bytes) {
        MessageDigest crypto;
        try {
            crypto = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new BtException("Unexpected error", e);
        }
        return crypto.digest(bytes);
    }
}
