package com.personal.offerguardian;
interface ILiveDeclineInput {
    String declineVerified(int x, int y, int width, int height, long deadline) = 0;
    void destroy() = 16777114;
}
