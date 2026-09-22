package dev.applingo;

/** Permission readiness is based on a live privileged read, never a saved Boolean. */
final class AccessGate {
    enum Mode { SHIZUKU, ROOT }
    enum Phase { SETUP, CHECKING, READY }
    static final class Snapshot {
        final Mode mode; final Phase phase; final String message;
        Snapshot(Mode mode, Phase phase, String message) { this.mode=mode; this.phase=phase; this.message=message; }
    }
    private Mode mode;
    private Phase phase=Phase.SETUP;
    private String message="Choose how AppLingo connects.";
    private long generation;
    AccessGate(Mode mode) { this.mode=mode; }
    Snapshot snapshot() { return new Snapshot(mode,phase,message); }
    void select(Mode next) { generation++; mode=next; phase=Phase.SETUP; message=next==Mode.ROOT ? "A rooted phone is required. Your root manager will ask for permission." : "Start Shizuku, then allow AppLingo to connect."; }
    long begin(String message) { generation++;phase=Phase.CHECKING;this.message=message;return generation; }
    boolean current(long token) { return token==generation && phase==Phase.CHECKING; }
    boolean ready(long token) { if(!current(token))return false;phase=Phase.READY;message=mode==Mode.ROOT?"Root connected":"Shizuku connected";return true; }
    boolean fail(long token,String message) { if(!current(token))return false;phase=Phase.SETUP;this.message=message;return true; }
    void lost(String message) { generation++;phase=Phase.SETUP;this.message=message; }
}
