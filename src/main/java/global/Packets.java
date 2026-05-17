package global;
public final class Packets {
    public static final byte AUTH_REQUEST  = 1;
    public static final byte AUTH_SUCCESS  = 2;
    public static final byte AUTH_FAILED   = 3;
    public static final byte BLOCK_BREAK   = 4;
    public static final byte BLOCK_PLACE   = 5;
    public static final byte REQUEST_LEVEL = 6;
    public static final byte LEVEL_DATA    = 7;
    public static final byte CHAT          = 8;
    public static final byte KEEPALIVE     = 9;
    public static final byte CONNECTION    = 10;
    public static final byte POS           = 11;
    public static final byte SET_POS       = 12;
    public static final byte CHUNK_DATA    = 13;
    public static final byte CHUNK_UNLOAD  = 14;
    public static final byte SKIN_UPLOAD   = 15;
    public static final byte SKIN_DATA     = 16;
    public static final byte AUTH_TOKEN    = 17;
    public static final byte TIME_OF_DAY   = 18;
    public static final byte PING_INFO     = 19;
}