package com.spotifyclient.media;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.util.Locale;

public final class WindowsMediaReader {
    private static final GUID IID_MANAGER_STATICS = GUID.fromString("{2050c4ee-11a0-57de-aed7-c97c70338245}");
    private static final GUID IID_ASYNC_INFO = GUID.fromString("{00000036-0000-0000-C000-000000000046}");
    private static final String MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager";
    private static final int RELEASE = 2;
    private static final int QUERY_INTERFACE = 0;
    private static final int STATICS_REQUEST_ASYNC = 6;
    private static final int ASYNC_OP_GET_RESULTS = 8;
    private static final int ASYNC_INFO_GET_STATUS = 7;
    private static final int MANAGER_GET_SESSIONS = 7;
    private static final int VECTOR_GET_AT = 6;
    private static final int VECTOR_GET_SIZE = 7;
    private static final int SESSION_GET_APP_ID = 6;
    private static final int SESSION_GET_MEDIA_PROPERTIES_ASYNC = 7;
    private static final int SESSION_GET_TIMELINE = 8;
    private static final int SESSION_GET_PLAYBACK_INFO = 9;
    private static final int PROPS_GET_TITLE = 6;
    private static final int PROPS_GET_ARTIST = 9;
    private static final int PROPS_GET_ALBUM = 10;
    private static final int TIMELINE_GET_START = 6;
    private static final int TIMELINE_GET_END = 7;
    private static final int TIMELINE_GET_POSITION = 10;
    private static final int TIMELINE_GET_LAST_UPDATED = 11;
    private static final int PLAYBACK_GET_STATUS = 7;
    private static final int STATUS_PLAYING = 4;
    private static final long TICKS_PER_MS = 10000L;
    private static final long FILETIME_EPOCH_OFFSET_MS = 11644473600000L;
    private static NativeLibrary combase;
    private Pointer manager;

    public static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    public boolean open() {
        try {
            if (combase == null) {
                combase = NativeLibrary.getInstance("combase");
            }

            combase.getFunction("RoInitialize").invokeInt(new Object[]{1});
            PointerByReference className = new PointerByReference();
            check(
                combase.getFunction("WindowsCreateString")
                    .invokeInt(
                        new Object[]{
                            new WString("Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager"),
                            "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager".length(),
                            className
                        }
                    ),
                "WindowsCreateString"
            );
            PointerByReference statics = new PointerByReference();

            try {
                check(
                    combase.getFunction("RoGetActivationFactory").invokeInt(new Object[]{className.getValue(), IID_MANAGER_STATICS, statics}),
                    "RoGetActivationFactory"
                );
            } finally {
                combase.getFunction("WindowsDeleteString").invokeInt(new Object[]{className.getValue()});
            }

            PointerByReference op = new PointerByReference();
            check(call(statics.getValue(), 6, op), "RequestAsync");
            release(statics.getValue());
            this.manager = awaitResult(op.getValue());
            return this.manager != null;
        } catch (Throwable t) {
            this.close();
            return false;
        }
    }

    public WindowsMediaReader.Snapshot read() {
        if (this.manager == null) {
            return null;
        }

        Pointer session = this.findSpotifySession();
        if (session == null) {
            return null;
        }

        try {
            PointerByReference propsOp = new PointerByReference();
            check(call(session, 7, propsOp), "TryGetMediaPropertiesAsync");
            Pointer props = awaitResult(propsOp.getValue());
            if (props == null) {
                return null;
            }

            String title;
            String artist;
            String album;
            try {
                title = getString(props, 6);
                artist = getString(props, 9);
                album = getString(props, 10);
            } finally {
                release(props);
            }

            if (title.isEmpty()) {
                return null;
            }

            PointerByReference timelineRef = new PointerByReference();
            check(call(session, 8, timelineRef), "GetTimelineProperties");
            Pointer timeline = timelineRef.getValue();

            long start;
            long end;
            long position;
            long lastUpdated;
            try {
                start = getLong(timeline, 6);
                end = getLong(timeline, 7);
                position = getLong(timeline, 10);
                lastUpdated = getLong(timeline, 11);
            } finally {
                release(timeline);
            }

            PointerByReference infoRef = new PointerByReference();
            check(call(session, 9, infoRef), "GetPlaybackInfo");
            IntByReference status = new IntByReference();

            try {
                check(call(infoRef.getValue(), 7, status), "get_PlaybackStatus");
            } finally {
                release(infoRef.getValue());
            }

            long sampledAt = lastUpdated > 0L ? lastUpdated / 10000L - 11644473600000L : System.currentTimeMillis();
            sampledAt = Math.min(sampledAt, System.currentTimeMillis());
            return new WindowsMediaReader.Snapshot(title, artist, album, (position - start) / 10000L, (end - start) / 10000L, status.getValue() == 4, sampledAt);
        } finally {
            release(session);
        }
    }

    public void close() {
        release(this.manager);
        this.manager = null;
    }

    private Pointer findSpotifySession() {
        PointerByReference listRef = new PointerByReference();
        if (call(this.manager, 7, listRef) < 0) {
            return null;
        }

        Pointer list = listRef.getValue();

        try {
            IntByReference size = new IntByReference();
            if (call(list, 7, size) < 0) {
                return null;
            }

            for (int i = 0; i < size.getValue(); i++) {
                PointerByReference sessionRef = new PointerByReference();
                if (call(list, 6, i, sessionRef) >= 0) {
                    Pointer session = sessionRef.getValue();
                    if (getString(session, 6).toLowerCase(Locale.ROOT).contains("spotify")) {
                        return session;
                    }

                    release(session);
                }
            }

            return null;
        } finally {
            release(list);
        }
    }

    private static Pointer awaitResult(Pointer op) {
        if (op == null) {
            return null;
        }

        try {
            PointerByReference infoRef = new PointerByReference();
            check(call(op, 0, IID_ASYNC_INFO, infoRef), "QueryInterface(IAsyncInfo)");
            Pointer info = infoRef.getValue();

            try {
                IntByReference status = new IntByReference();
                long deadline = System.currentTimeMillis() + 2000L;

                while (true) {
                    check(call(info, 7, status), "get_Status");
                    if (status.getValue() != 0) {
                        if (status.getValue() != 1) {
                            return null;
                        }
                        break;
                    }

                    if (System.currentTimeMillis() > deadline) {
                        return null;
                    }

                    Thread.sleep(5L);
                }
            } finally {
                release(info);
            }

            PointerByReference var18 = new PointerByReference();
            check(call(op, 8, var18), "GetResults");
            return var18.getValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            release(op);
        }
    }

    private static String getString(Pointer obj, int slot) {
        PointerByReference hstring = new PointerByReference();
        if (call(obj, slot, hstring) >= 0 && hstring.getValue() != null) {
            try {
                IntByReference length = new IntByReference();
                Pointer chars = (Pointer)combase.getFunction("WindowsGetStringRawBuffer").invoke(Pointer.class, new Object[]{hstring.getValue(), length});
                return chars == null ? "" : new String(chars.getCharArray(0L, length.getValue()));
            } finally {
                combase.getFunction("WindowsDeleteString").invokeInt(new Object[]{hstring.getValue()});
            }
        } else {
            return "";
        }
    }

    private static long getLong(Pointer obj, int slot) {
        LongByReference value = new LongByReference();
        check(call(obj, slot, value), "get (slot " + slot + ")");
        return value.getValue();
    }

    private static int call(Pointer obj, int slot, Object... args) {
        Pointer vtable = obj.getPointer(0L);
        Function method = Function.getFunction(vtable.getPointer((long)slot * Native.POINTER_SIZE));
        Object[] full = new Object[args.length + 1];
        full[0] = obj;
        System.arraycopy(args, 0, full, 1, args.length);
        return method.invokeInt(full);
    }

    private static void release(Pointer obj) {
        if (obj != null) {
            call(obj, 2);
        }
    }

    private static void check(int hresult, String what) {
        if (hresult < 0) {
            throw new IllegalStateException(what + " failed: 0x" + Integer.toHexString(hresult));
        }
    }

    public record Snapshot(String title, String artist, String album, long positionMs, long durationMs, boolean playing, long sampledAt) {
        public long positionNow() {
            long p = this.playing ? this.positionMs + (System.currentTimeMillis() - this.sampledAt) : this.positionMs;
            return Math.max(0L, this.durationMs > 0L ? Math.min(p, this.durationMs) : p);
        }
    }
}
