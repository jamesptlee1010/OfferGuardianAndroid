package com.personal.offerguardian;
interface IOwnTouchProbe {
    String probe() = 0;
    String tapOwnTarget(int x, int y) = 1;
    void destroy() = 16777114;
}
