package vn.edu.hust.kat.common.utils;

import lombok.Getter;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class NumericConversion {
    public static final Base36Encoder base36Encoder = new Base36Encoder();
    public static final Base36Decoder base36Decoder = new Base36Decoder();

    public static class Base36Encoder {

        private static final byte[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes();

        private Base36Encoder() {}

        public byte[] encode(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return new byte[0];
            }

            java.math.BigInteger value =
                    new java.math.BigInteger(1, bytes);

            if (value.signum() == 0) {
                return new byte[]{'0'};
            }

            StringBuilder result = new StringBuilder();

            while (value.signum() > 0) {
                java.math.BigInteger[] divRem =
                        value.divideAndRemainder(java.math.BigInteger.valueOf(36));

                result.append((char) ALPHABET[divRem[1].intValue()]);
                value = divRem[0];
            }

            return result.reverse().toString().getBytes();
        }

        public String encode(Long value) {
            return Long.toString(value, 36).toUpperCase();
        }
    }

    public static class Base36Decoder {

        public byte[] decode(byte[] encoded) {
            if (encoded == null || encoded.length == 0) {
                return new byte[0];
            }

            java.math.BigInteger value = java.math.BigInteger.ZERO;

            for (byte b : encoded) {
                int digit = Character.digit((char) b, 36);

                if (digit == -1) {
                    throw new IllegalArgumentException(
                            "Invalid Base36 character: " + (char) b
                    );
                }

                value = value
                        .multiply(java.math.BigInteger.valueOf(36))
                        .add(java.math.BigInteger.valueOf(digit));
            }

            byte[] result = value.toByteArray();

            // BigInteger có thể thêm byte 0 ở đầu để biểu diễn số dương.
            if (result.length > 1 && result[0] == 0) {
                return java.util.Arrays.copyOfRange(
                        result, 1, result.length
                );
            }

            return result;
        }

        public Long decode(String encoded){
            boolean valid = encoded.chars().allMatch(c -> Character.digit(c, 36) != -1) &&
                    encoded != null &&
                    !encoded.isEmpty();

            if (!valid) {
                throw new IllegalArgumentException("The encoded string must only contains characters from range 0-9 or a-z/A-Z ");
            }

            return Long.parseLong(encoded, 36);
        }
    }
}