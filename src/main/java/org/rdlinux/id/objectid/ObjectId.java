/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.rdlinux.id.objectid;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * <p>A globally unique identifier for objects.</p>
 *
 * <p>Consists of 12 bytes, divided as follows:</p>
 * <table border="1">
 *     <caption>ObjectID layout</caption>
 *     <tr>
 *         <td>0</td><td>1</td><td>2</td><td>3</td><td>4</td><td>5</td><td>6</td><td>7</td><td>8</td><td>9</td><td>10</td><td>11</td>
 *     </tr>
 *     <tr>
 *         <td colspan="4">time</td><td colspan="5">random value</td><td colspan="3">inc</td>
 *     </tr>
 * </table>
 *
 * <p>Instances of this class are immutable.</p>
 */
public final class ObjectId implements Comparable<ObjectId>, Serializable {

    private static final long serialVersionUID = 3670079982654483072L;

    private static final int OBJECT_ID_LENGTH = 12;
    private static final int LOW_ORDER_THREE_BYTES = 0x00ffffff;

    // Use primitives to represent the 5-byte random value.
    private static final int RANDOM_VALUE1;
    private static final short RANDOM_VALUE2;

    private static final AtomicInteger NEXT_COUNTER = new AtomicInteger(new SecureRandom().nextInt());

    private static final char[] HEX_CHARS = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    static {
        try {
            SecureRandom secureRandom = new SecureRandom();
            RANDOM_VALUE1 = secureRandom.nextInt(0x01000000);
            RANDOM_VALUE2 = (short) secureRandom.nextInt(0x00008000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final int timestamp;
    private final int counter;
    private final int randomValue1;
    private final short randomValue2;

    /**
     * Create a new object id.
     */
    public ObjectId() {
        this(new Date());
    }

    /**
     * Constructs a new instance using the given date.
     *
     * @param date the date
     */
    public ObjectId(final Date date) {
        this(dateToTimestampSeconds(date), NEXT_COUNTER.getAndIncrement() & LOW_ORDER_THREE_BYTES, false);
    }

    /**
     * Constructs a new instances using the given date and counter.
     *
     * @param date    the date
     * @param counter the counter
     * @throws IllegalArgumentException if the high order byte of counter is not zero
     */
    public ObjectId(final Date date, final int counter) {
        this(dateToTimestampSeconds(date), counter, true);
    }

    /**
     * Constructs a new instances using the given date, machine identifier, process identifier, and counter.
     *
     * @param date              the date
     * @param machineIdentifier the machine identifier
     * @param processIdentifier the process identifier
     * @param counter           the counter
     * @throws IllegalArgumentException if the high order byte of machineIdentifier or counter is not zero
     * @deprecated Use {@link #ObjectId(Date, int)} instead
     */
    @Deprecated
    public ObjectId(final Date date, final int machineIdentifier, final short processIdentifier, final int counter) {
        this(dateToTimestampSeconds(date), machineIdentifier, processIdentifier, counter);
    }

    /**
     * Creates an ObjectId using the given time, machine identifier, process identifier, and counter.
     *
     * @param timestamp         the time in seconds
     * @param machineIdentifier the machine identifier
     * @param processIdentifier the process identifier
     * @param counter           the counter
     * @throws IllegalArgumentException if the high order byte of machineIdentifier or counter is not zero
     * @deprecated Use {@link #ObjectId(int, int)} instead
     */
    @Deprecated
    public ObjectId(final int timestamp, final int machineIdentifier, final short processIdentifier, final int counter) {
        this(timestamp, machineIdentifier, processIdentifier, counter, true);
    }

    /**
     * Creates an ObjectId using the given time, machine identifier, process identifier, and counter.
     *
     * @param timestamp the time in seconds
     * @param counter   the counter
     * @throws IllegalArgumentException if the high order byte of counter is not zero
     */
    public ObjectId(final int timestamp, final int counter) {
        this(timestamp, counter, true);
    }

    private ObjectId(final int timestamp, final int counter, final boolean checkCounter) {
        this(timestamp, RANDOM_VALUE1, RANDOM_VALUE2, counter, checkCounter);
    }

    private ObjectId(final int timestamp, final int randomValue1, final short randomValue2, final int counter,
                     final boolean checkCounter) {
        if ((randomValue1 & 0xff000000) != 0) {
            throw new IllegalArgumentException("The machine identifier must be between 0 and 16777215 (it must fit in three bytes).");
        }
        if (checkCounter && ((counter & 0xff000000) != 0)) {
            throw new IllegalArgumentException("The counter must be between 0 and 16777215 (it must fit in three bytes).");
        }
        this.timestamp = timestamp;
        this.counter = counter & LOW_ORDER_THREE_BYTES;
        this.randomValue1 = randomValue1;
        this.randomValue2 = randomValue2;
    }

    /**
     * Constructs a new instance from a 24-byte hexadecimal string representation.
     *
     * @param hexString the string to convert
     * @throws IllegalArgumentException if the string is not a valid hex string representation of an ObjectId
     */
    public ObjectId(final String hexString) {
        this(parseHexString(hexString));
    }

    /**
     * Constructs a new instance from the given byte array
     *
     * @param bytes the byte array
     * @throws IllegalArgumentException if array is null or not of length 12
     */
    public ObjectId(final byte[] bytes) {
        this(ByteBuffer.wrap(Assertions.isTrueArgument("bytes has length of 12", bytes, Assertions.notNull("bytes", bytes).length == 12)));
    }

    /**
     * Creates an ObjectId
     *
     * @param timestamp                   time in seconds
     * @param machineAndProcessIdentifier machine and process identifier
     * @param counter                     incremental value
     */
    ObjectId(final int timestamp, final int machineAndProcessIdentifier, final int counter) {
        this(legacyToBytes(timestamp, machineAndProcessIdentifier, counter));
    }

    /**
     * Constructs a new instance from the given ByteBuffer
     *
     * @param buffer the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     * @since 3.4
     */
    public ObjectId(final ByteBuffer buffer) {
        Assertions.notNull("buffer", buffer);
        Assertions.isTrueArgument("buffer.remaining() >=12", buffer.remaining() >= OBJECT_ID_LENGTH);

        // Note: Cannot use ByteBuffer.getInt because it depends on tbe buffer's byte order
        // and ObjectId's are always in big-endian order.
        this.timestamp = makeInt(buffer.get(), buffer.get(), buffer.get(), buffer.get());
        this.randomValue1 = makeInt((byte) 0, buffer.get(), buffer.get(), buffer.get());
        this.randomValue2 = makeShort(buffer.get(), buffer.get());
        this.counter = makeInt((byte) 0, buffer.get(), buffer.get(), buffer.get());
    }

    /**
     * Gets a new object id.
     *
     * @return the new id
     */
    public static ObjectId get() {
        return new ObjectId();
    }

    /**
     * Checks if a string could be an {@code ObjectId}.
     *
     * @param hexString a potential ObjectId as a String.
     * @return whether the string could be an object id
     * @throws IllegalArgumentException if hexString is null
     */
    public static boolean isValid(final String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException();
        }

        int len = hexString.length();
        if (len != 24) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            char c = hexString.charAt(i);
            if (c >= '0' && c <= '9') {
                continue;
            }
            if (c >= 'a' && c <= 'f') {
                continue;
            }
            if (c >= 'A' && c <= 'F') {
                continue;
            }

            return false;
        }

        return true;
    }

    private static byte[] legacyToBytes(final int timestamp, final int machineAndProcessIdentifier, final int counter) {
        byte[] bytes = new byte[OBJECT_ID_LENGTH];
        bytes[0] = int3(timestamp);
        bytes[1] = int2(timestamp);
        bytes[2] = int1(timestamp);
        bytes[3] = int0(timestamp);
        bytes[4] = int3(machineAndProcessIdentifier);
        bytes[5] = int2(machineAndProcessIdentifier);
        bytes[6] = int1(machineAndProcessIdentifier);
        bytes[7] = int0(machineAndProcessIdentifier);
        bytes[8] = int3(counter);
        bytes[9] = int2(counter);
        bytes[10] = int1(counter);
        bytes[11] = int0(counter);
        return bytes;
    }

    /**
     * <p>Creates an ObjectId using time, machine and inc values.  The Java driver used to create all ObjectIds this way, but it does not
     * match the <a href="http://docs.mongodb.org/manual/reference/object-id/">ObjectId specification</a>, which requires four values, not
     * three. This major release of the Java driver conforms to the specification, but still supports clients that are relying on the
     * behavior of the previous major release by providing this explicit factory method that takes three parameters instead of four.</p>
     *
     * <p>Ordinary users of the driver will not need this method.  It's only for those that have written there own BSON decoders.</p>
     *
     * <p>NOTE: This will not break any application that use ObjectIds.  The 12-byte representation will be round-trippable from old to new
     * driver releases.</p>
     *
     * @param time    time in seconds
     * @param machine machine ID
     * @param inc     incremental value
     * @return a new {@code ObjectId} created from the given values
     * @since 2.12.0
     * @deprecated Use {@link #ObjectId(int, int)} instead
     */
    @Deprecated
    public static ObjectId createFromLegacyFormat(final int time, final int machine, final int inc) {
        return new ObjectId(time, machine, inc);
    }

    /**
     * Gets the current value of the auto-incrementing counter.
     *
     * @return the current counter value.
     * @deprecated
     */
    @Deprecated
    public static int getCurrentCounter() {
        return NEXT_COUNTER.get() & LOW_ORDER_THREE_BYTES;
    }

    /**
     * Gets the generated machine identifier.
     *
     * @return an int representing the machine identifier
     * @deprecated
     */
    @Deprecated
    public static int getGeneratedMachineIdentifier() {
        return RANDOM_VALUE1;
    }

    /**
     * Gets the generated process identifier.
     *
     * @return the process id
     * @deprecated
     */
    @Deprecated
    public static int getGeneratedProcessIdentifier() {
        return RANDOM_VALUE2;
    }

    private static byte[] parseHexString(final String s) {
        if (!isValid(s)) {
            throw new IllegalArgumentException("invalid hexadecimal representation of an ObjectId: [" + s + "]");
        }

        byte[] b = new byte[OBJECT_ID_LENGTH];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return b;
    }

    private static int dateToTimestampSeconds(final Date time) {
        return (int) (time.getTime() / 1000);
    }

    private static int makeInt(final byte b3, final byte b2, final byte b1, final byte b0) {
        // CHECKSTYLE:OFF
        return (((b3) << 24) |
                ((b2 & 0xff) << 16) |
                ((b1 & 0xff) << 8) |
                ((b0 & 0xff)));
        // CHECKSTYLE:ON
    }

    private static short makeShort(final byte b1, final byte b0) {
        // CHECKSTYLE:OFF
        return (short) (((b1 & 0xff) << 8) | ((b0 & 0xff)));
        // CHECKSTYLE:ON
    }

    // Deprecated methods

    private static byte int3(final int x) {
        return (byte) (x >> 24);
    }

    private static byte int2(final int x) {
        return (byte) (x >> 16);
    }

    private static byte int1(final int x) {
        return (byte) (x >> 8);
    }

    private static byte int0(final int x) {
        return (byte) (x);
    }

    private static byte short1(final short x) {
        return (byte) (x >> 8);
    }

    private static byte short0(final short x) {
        return (byte) (x);
    }

    /**
     * Convert to a byte array.  Note that the numbers are stored in big-endian order.
     *
     * @return the byte array
     */
    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(OBJECT_ID_LENGTH);
        this.putToByteBuffer(buffer);
        return buffer.array();  // using .allocate ensures there is a backing array that can be returned
    }

    /**
     * Convert to bytes and put those bytes to the provided ByteBuffer.
     * Note that the numbers are stored in big-endian order.
     *
     * @param buffer the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     * @since 3.4
     */
    public void putToByteBuffer(final ByteBuffer buffer) {
        Assertions.notNull("buffer", buffer);
        Assertions.isTrueArgument("buffer.remaining() >=12", buffer.remaining() >= OBJECT_ID_LENGTH);

        buffer.put(int3(this.timestamp));
        buffer.put(int2(this.timestamp));
        buffer.put(int1(this.timestamp));
        buffer.put(int0(this.timestamp));
        buffer.put(int2(this.randomValue1));
        buffer.put(int1(this.randomValue1));
        buffer.put(int0(this.randomValue1));
        buffer.put(short1(this.randomValue2));
        buffer.put(short0(this.randomValue2));
        buffer.put(int2(this.counter));
        buffer.put(int1(this.counter));
        buffer.put(int0(this.counter));
    }

    /**
     * Gets the timestamp (number of seconds since the Unix epoch).
     *
     * @return the timestamp
     */
    public int getTimestamp() {
        return this.timestamp;
    }

    /**
     * Gets the timestamp as a {@code Date} instance.
     *
     * @return the Date
     */
    public Date getDate() {
        return new Date((this.timestamp & 0xFFFFFFFFL) * 1000L);
    }

    /**
     * Converts this instance into a 24-byte hexadecimal string representation.
     *
     * @return a string representation of the ObjectId in hexadecimal format
     */
    public String toHexString() {
        char[] chars = new char[OBJECT_ID_LENGTH * 2];
        int i = 0;
        for (byte b : this.toByteArray()) {
            chars[i++] = HEX_CHARS[b >> 4 & 0xF];
            chars[i++] = HEX_CHARS[b & 0xF];
        }
        return new String(chars);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }

        ObjectId objectId = (ObjectId) o;

        if (this.counter != objectId.counter) {
            return false;
        }
        if (this.timestamp != objectId.timestamp) {
            return false;
        }

        if (this.randomValue1 != objectId.randomValue1) {
            return false;
        }

        if (this.randomValue2 != objectId.randomValue2) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = this.timestamp;
        result = 31 * result + this.counter;
        result = 31 * result + this.randomValue1;
        result = 31 * result + this.randomValue2;
        return result;
    }

    // Big-Endian helpers, in this class because all other BSON numbers are little-endian

    @Override
    public int compareTo(final ObjectId other) {
        if (other == null) {
            throw new NullPointerException();
        }

        byte[] byteArray = this.toByteArray();
        byte[] otherByteArray = other.toByteArray();
        for (int i = 0; i < OBJECT_ID_LENGTH; i++) {
            if (byteArray[i] != otherByteArray[i]) {
                return ((byteArray[i] & 0xff) < (otherByteArray[i] & 0xff)) ? -1 : 1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return this.toHexString();
    }

    /**
     * Gets the machine identifier.
     *
     * @return the machine identifier
     * @deprecated
     */
    @Deprecated
    public int getMachineIdentifier() {
        return this.randomValue1;
    }

    /**
     * Gets the process identifier.
     *
     * @return the process identifier
     * @deprecated
     */
    @Deprecated
    public short getProcessIdentifier() {
        return this.randomValue2;
    }

    /**
     * Gets the counter.
     *
     * @return the counter
     * @deprecated
     */
    @Deprecated
    public int getCounter() {
        return this.counter;
    }

    /**
     * Gets the time of this ID, in seconds.
     *
     * @return the time component of this ID in seconds
     * @deprecated Use #getTimestamp instead
     */
    @Deprecated
    public int getTimeSecond() {
        return this.timestamp;
    }

    /**
     * Gets the time of this instance, in milliseconds.
     *
     * @return the time component of this ID in milliseconds
     * @deprecated Use #getDate instead
     */
    @Deprecated
    public long getTime() {
        return (this.timestamp & 0xFFFFFFFFL) * 1000L;
    }

    /**
     * @return a string representation of the ObjectId in hexadecimal format
     * @see ObjectId#toHexString()
     * @deprecated use {@link #toHexString()}
     */
    @Deprecated
    public String toStringMongod() {
        return this.toHexString();
    }

}
