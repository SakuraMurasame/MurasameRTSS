package io.perfoverlay.app;
interface IHardwareService {
    String sample(int mask) = 0;
    String battery() = 1;
    void destroy() = 16777114;
}
