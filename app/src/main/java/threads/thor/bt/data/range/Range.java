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

package threads.thor.bt.data.range;

import java.nio.ByteBuffer;

import threads.thor.bt.net.buffer.ByteBufferView;

/**
 * Represents a range of binary data.
 *
 * @since 1.3
 */
public interface Range<T extends Range<T>> {

    /**
     * @return Length of this data range
     * @since 1.3
     */
    long length();

    /**
     * Build a subrange of this data range.
     *
     * @param offset Offset from the beginning of the original data range in bytes, inclusive
     * @param length Length of the new data range
     * @return Subrange of the original data range
     * @since 1.3
     */
    Range<T> getSubrange(long offset, long length);

    /**
     * Build a subrange of this data range.
     *
     * @param offset Offset from the beginning of the original data range in bytes, inclusive
     * @return Subrange of the original data range
     * @since 1.3
     */
    Range<T> getSubrange(long offset);

    /**
     * Get all data in this range
     *
     * @return Data in this range
     * @since 1.3
     */
    byte[] getBytes();

    /**
     * Read all data in this range to the provided buffer.
     * If there is not enough space in the buffer, then no bytes will be read.
     *
     * @return true, if all data in the range has been read into the buffer
     * @since 1.9
     */
    boolean getBytes(ByteBuffer buffer);

    /**
     * Put data at the beginning of this range.
     *
     * @param block Block of data with length less than or equal to {@link #length()} of this range
     * @throws IllegalArgumentException if data does not fit in this range
     * @since 1.3
     */
    void putBytes(byte[] block);

    /**
     * Put data from the provided buffer at the beginning of this range.
     *
     * @since 1.9
     */
    void putBytes(ByteBufferView buffer);
}
