package org.rdlinux.id.uuid;

public class UUID {
    public static String next() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
