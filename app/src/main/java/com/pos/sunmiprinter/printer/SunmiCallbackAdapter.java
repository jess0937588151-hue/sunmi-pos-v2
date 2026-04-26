package com.pos.sunmiprinter.printer;

import android.os.RemoteException;
import android.util.Log;

import woyou.aidlservice.jiuiv5.ICallback;

public class SunmiCallbackAdapter extends ICallback.Stub {

    private static final String TAG = "SunmiCallback";

    @Override
    public void onRunResult(boolean isSuccess) throws RemoteException {
        Log.d(TAG, "onRunResult=" + isSuccess);
    }

    @Override
    public void onReturnString(String result) throws RemoteException {
        Log.d(TAG, "onReturnString=" + result);
    }

    @Override
    public void onRaiseException(int code, String msg) throws RemoteException {
        Log.e(TAG, "onRaiseException code=" + code + ", msg=" + msg);
    }

    @Override
    public void onPrintResult(int code, String msg) throws RemoteException {
        Log.d(TAG, "onPrintResult code=" + code + ", msg=" + msg);
    }
}
