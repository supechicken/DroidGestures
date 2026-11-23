package dev.supechicken666.droidgestures;

interface IGestureService {
    void destroy() = 16777114;
    void restart() = 1;
    boolean setGestureCmd(in @nullable String gesture, in @nullable String command) = 2;
}
