package dev.infectedware.skysong;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class ShareCode {
    public static final String PREFIX = "VF2";
    private static final String B62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int FALSE = 0;
    private static final int TRUE = 1;
    private static final int NUMBER = 2;
    private static final int TEXT = 3;
    private static final int MAX_BYTES = 65536;
    private static final byte[] DICT = "SKYSONG BLUE_PINK BLUE_GREEN DARK_BLUE BLACK_WHITE Click GUI=344:0;watermark=5.0,5.0;targethud=arraylist=armor=inventory=hpfocus=keystrokes=lyricsbar=nowplaying=potions=hotkeys=cooldowns=.0,.5,;0000000000000000000000000000000000000000000000000000000000000"
        .getBytes(StandardCharsets.US_ASCII);
    private static List<String> table;

    private ShareCode() {
    }

    public static String encode() {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        List<String> table = table();
        TreeMap<Integer, Field> changed = new TreeMap<>();

        try {
            for (Field f : ModConfig.sharedFields()) {
                if (!Objects.equals(f.get(null), ModConfig.defaultOf(f))) {
                    int idx = table.indexOf(f.getName());
                    if (idx >= 0) {
                        changed.put(idx, f);
                    }
                }
            }

            int last = -1;

            for (Entry<Integer, Field> e : changed.entrySet()) {
                Object v = e.getValue().get(null);
                int gap = e.getKey() - last;
                last = e.getKey();
                if (v instanceof Boolean b) {
                    varint(raw, (long)gap << 2 | (b ? 1 : 0));
                } else if (v instanceof Integer i) {
                    varint(raw, (long)gap << 2 | 2L);
                    varint(raw, zigzag(i.intValue()));
                } else if (v instanceof Float fl) {
                    varint(raw, (long)gap << 2 | 2L);
                    varint(raw, zigzag(Math.round(fl.floatValue() * 1000.0)));
                } else if (v instanceof String str) {
                    varint(raw, (long)gap << 2 | 3L);
                    byte[] b = str.getBytes(StandardCharsets.UTF_8);
                    varint(raw, b.length);
                    raw.write(b, 0, b.length);
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        CRC32 crc = new CRC32();
        crc.update(raw.toByteArray());
        long c = crc.getValue();

        for (int s = 24; s >= 0; s -= 8) {
            raw.write((int)(c >>> s) & 0xFF);
        }

        Deflater d = new Deflater(9, true);
        d.setDictionary(DICT);
        d.setInput(raw.toByteArray());
        d.finish();
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        packed.write(1);
        byte[] buf = new byte[4096];

        while (!d.finished()) {
            packed.write(buf, 0, d.deflate(buf));
        }

        d.end();
        String body = base62(packed.toByteArray());
        return "VF2" + twoChars(body.length()) + body;
    }

    public static ShareCode.Result decode(String text) {
        if (text == null) {
            return fail("Nothing was pasted");
        }

        String flat = text.replaceAll("[\\s\\u200B-\\u200D\\u2060\\uFEFF]", "");
        int at = flat.indexOf("VF2");
        if (at < 0) {
            return flat.contains("VF1-") ? decodeV1(flat.substring(flat.indexOf("VF1-"))) : fail("No Skysong code in that text (codes start with VF2)");
        }

        String rest = flat.substring(at + "VF2".length());
        if (rest.length() < 2) {
            return fail("The code is cut off: copy all of it");
        }

        int need = fromTwoChars(rest.substring(0, 2));
        if (need < 0) {
            return fail("The code is damaged: copy it again");
        }

        String body = rest.substring(2);
        int have = 0;

        while (have < body.length() && have < need && "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".indexOf(body.charAt(have)) >= 0) {
            have++;
        }

        if (have < need) {
            return fail("The code is cut off (" + (have + 5) + " of " + (need + 5) + " characters): copy all of it");
        }

        body = body.substring(0, need);
        byte[] packed = unbase62(body);
        if (packed != null && packed.length >= 2 && packed[0] == 1) {
            byte[] raw;
            try {
                Inflater inf = new Inflater(true);
                inf.setDictionary(DICT);
                inf.setInput(packed, 1, packed.length - 1);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];

                while (!inf.finished()) {
                    int n = inf.inflate(buf);
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                        break;
                    }

                    out.write(buf, 0, n);
                    if (out.size() > 65536) {
                        return fail("The code is damaged: copy it again");
                    }
                }

                inf.end();
                raw = out.toByteArray();
            } catch (DataFormatException | IllegalArgumentException e) {
                return fail("The code is damaged: copy it again");
            }

            if (raw.length < 4) {
                return fail("The code is damaged: copy it again");
            }

            int n = raw.length - 4;
            CRC32 crc = new CRC32();
            crc.update(raw, 0, n);
            long want = (raw[n] & 255L) << 24 | (raw[n + 1] & 255L) << 16 | (raw[n + 2] & 255L) << 8 | raw[n + 3] & 255L;
            if (crc.getValue() != want) {
                return fail("The code is damaged: copy it again");
            }

            Map<String, Field> byName = new HashMap<>();

            for (Field f : ModConfig.sharedFields()) {
                byName.put(f.getName(), f);
            }

            List<String> table = table();
            Map<String, Object> settings = new LinkedHashMap<>();
            int[] pos = new int[]{0};
            long index = -1L;

            try {
                while (pos[0] < n) {
                    long h = readVarint(raw, pos, n);
                    int kind = (int)(h & 3L);
                    index += h >>> 2;
                    Field f = index >= 0L && index < table.size() ? byName.get(table.get((int)index)) : null;

                    Object value = switch (kind) {
                        case 0 -> Boolean.FALSE;
                        case 1 -> Boolean.TRUE;
                        case 2 -> unzigzag(readVarint(raw, pos, n));
                        default -> {
                            int len = (int)readVarint(raw, pos, n);
                            if (len < 0 || pos[0] + len > n) {
                                throw new IndexOutOfBoundsException();
                            }

                            String str = new String(raw, pos[0], len, StandardCharsets.UTF_8);
                            pos[0] += len;
                            yield str;
                        }
                    };
                    if (f != null) {
                        Class<?> t = f.getType();
                        if (t == boolean.class && value instanceof Boolean) {
                            settings.put(f.getName(), value);
                        } else if (t == int.class && value instanceof Long l) {
                            settings.put(f.getName(), (int)l.longValue());
                        } else if (t == float.class && value instanceof Long l) {
                            settings.put(f.getName(), (float)(l.longValue() / 1000.0));
                        } else if (t == String.class && value instanceof String) {
                            settings.put(f.getName(), value);
                        }
                    }
                }
            } catch (IndexOutOfBoundsException e) {
                return fail("The code is damaged: copy it again");
            }

            return new ShareCode.Result(settings, null);
        } else {
            return fail("The code is damaged: copy it again");
        }
    }

    private static ShareCode.Result decodeV1(String code) {
        JsonObject json = Profiles.decodeV1(code);
        if (json == null) {
            return fail("That old code is damaged (codes sent in Discord lose their _ ): ask for a new one");
        }

        Map<String, Object> settings = new LinkedHashMap<>();

        for (Field f : ModConfig.sharedFields()) {
            if (json.has(f.getName()) && json.get(f.getName()).isJsonPrimitive()) {
                JsonPrimitive p = json.getAsJsonPrimitive(f.getName());
                Class<?> t = f.getType();
                if (t == boolean.class && p.isBoolean()) {
                    settings.put(f.getName(), p.getAsBoolean());
                } else if (t == int.class && p.isNumber()) {
                    settings.put(f.getName(), p.getAsInt());
                } else if (t == float.class && p.isNumber()) {
                    settings.put(f.getName(), p.getAsFloat());
                } else if (t == String.class && p.isString()) {
                    settings.put(f.getName(), p.getAsString());
                }
            }
        }

        return new ShareCode.Result(settings, null);
    }

    private static ShareCode.Result fail(String why) {
        return new ShareCode.Result(Map.of(), why);
    }

    static List<String> table() {
        if (table != null) {
            return table;
        }

        List<String> out = new ArrayList<>();

        try (InputStream in = ShareCode.class.getResourceAsStream("/assets/skysong/share_ids.txt")) {
            if (in == null) {
                throw new IllegalStateException("share_ids.txt missing");
            }

            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        table = out;
        return out;
    }

    static List<String> missingIds() {
        List<String> missing = new ArrayList<>();

        for (Field f : ModConfig.sharedFields()) {
            if (!table().contains(f.getName())) {
                missing.add(f.getName());
            }
        }

        return missing;
    }

    private static long zigzag(long v) {
        return v << 1 ^ v >> 63;
    }

    private static long unzigzag(long v) {
        return v >>> 1 ^ -(v & 1L);
    }

    private static void varint(ByteArrayOutputStream out, long v) {
        while ((v & -128L) != 0L) {
            out.write((int)(v & 127L) | 128);
            v >>>= 7;
        }

        out.write((int)v);
    }

    private static long readVarint(byte[] b, int[] pos, int end) {
        long v = 0L;

        for (int shift = 0; shift < 64; shift += 7) {
            if (pos[0] >= end) {
                throw new IndexOutOfBoundsException();
            }

            int x = b[pos[0]++] & 255;
            v |= (long)(x & 127) << shift;
            if ((x & 128) == 0) {
                return v;
            }
        }

        throw new IndexOutOfBoundsException();
    }

    private static String base62(byte[] bytes) {
        BigInteger n = new BigInteger(1, bytes);
        BigInteger base = BigInteger.valueOf(62L);
        StringBuilder sb = new StringBuilder();

        while (n.signum() > 0) {
            BigInteger[] qr = n.divideAndRemainder(base);
            sb.append("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".charAt(qr[1].intValue()));
            n = qr[0];
        }

        return sb.reverse().toString();
    }

    private static byte[] unbase62(String s) {
        BigInteger n = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(62L);

        for (int i = 0; i < s.length(); i++) {
            int d = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".indexOf(s.charAt(i));
            if (d < 0) {
                return null;
            }

            n = n.multiply(base).add(BigInteger.valueOf(d));
        }

        byte[] b = n.toByteArray();
        return b.length > 1 && b[0] == 0 ? Arrays.copyOfRange(b, 1, b.length) : b;
    }

    private static String twoChars(int n) {
        if (n >= 3844) {
            throw new IllegalStateException("code too long");
        } else {
            return ""
                + "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".charAt(n / 62)
                + "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".charAt(n % 62);
        }
    }

    private static int fromTwoChars(String s) {
        int a = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".indexOf(s.charAt(0));
        int b = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".indexOf(s.charAt(1));
        return a >= 0 && b >= 0 ? a * 62 + b : -1;
    }

    public record Result(Map<String, Object> settings, String error) {
        public boolean ok() {
            return this.error == null;
        }
    }
}
