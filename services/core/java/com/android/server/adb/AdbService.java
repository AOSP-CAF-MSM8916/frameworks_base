/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.adb;

import android.content.Context;
import android.debug.AdbManagerInternal;
import android.debug.IAdbManager;
import android.debug.IAdbTransport;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.internal.util.DumpUtils;
import com.android.internal.util.IndentingPrintWriter;
import com.android.server.FgThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * The Android Debug Bridge (ADB) service. This controls the availability of ADB and authorization
 * of devices allowed to connect to ADB.
 */
public class AdbService extends IAdbManager.Stub {
    /**
     * Manages the service lifecycle for {@code AdbService} in {@code SystemServer}.
     */
    public static class Lifecycle extends SystemService {
        private AdbService mAdbService;

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            mAdbService = new AdbService(getContext());
            publishBinderService(Context.ADB_SERVICE, mAdbService);
        }

        @Override
        public void onBootPhase(int phase) {
            if (phase == SystemService.PHASE_ACTIVITY_MANAGER_READY) {
                mAdbService.systemReady();
            }
        }
    }

    private class AdbManagerInternalImpl extends AdbManagerInternal {
        @Override
        public void registerTransport(IAdbTransport transport) {
            mTransports.put(transport.asBinder(), transport);
        }

        @Override
        public void unregisterTransport(IAdbTransport transport) {
            mTransports.remove(transport.asBinder());
        }

        @Override
        public boolean isAdbEnabled() {
            return mAdbEnabled;
        }
    }

    private final class AdbHandler extends Handler {
        AdbHandler(Looper looper) {
            super(looper);
            try {
                // TODO: add initialization here.
            } catch (Exception e) {
                Slog.e(TAG, "Error initializing AdbHandler", e);
            }
        }

        public void sendMessage(int what, boolean arg) {
            removeMessages(what);
            Message m = Message.obtain(this, what);
            m.arg1 = (arg ? 1 : 0);
            sendMessage(m);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // TODO: handle messages here.
            }
        }
    }

    private static final String TAG = "AdbService";
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final AdbService.AdbHandler mHandler;
    private final ArrayMap<IBinder, IAdbTransport> mTransports = new ArrayMap<>();

    private boolean mAdbEnabled;

    private AdbService(Context context) {
        mContext = context;

        mHandler = new AdbHandler(FgThread.get().getLooper());

        LocalServices.addService(AdbManagerInternal.class, new AdbManagerInternalImpl());
    }

    /**
     * Called in response to {@code SystemService.PHASE_ACTIVITY_MANAGER_READY} from {@code
     * SystemServer}.
     */
    public void systemReady() {
        if (DEBUG) Slog.d(TAG, "systemReady");
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, writer)) return;

        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        final long ident = Binder.clearCallingIdentity();
        try {
            if (args == null || args.length == 0 || "-a".equals(args[0])) {
                pw.println("ADB Manager State:");
                pw.increaseIndent();
                pw.print("Number of registered transports: ");
                pw.println(mTransports.size());
            } else {
                pw.println("Dump current ADB state");
                pw.println("  No commands available");
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }
}
