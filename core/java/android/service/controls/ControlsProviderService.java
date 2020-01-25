/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.service.controls;

import android.annotation.NonNull;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.service.controls.actions.ControlAction;
import android.service.controls.templates.ControlTemplate;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Service implementation allowing applications to contribute controls to the
 * System UI.
 * @hide
 */
public abstract class ControlsProviderService extends Service {

    @SdkConstant(SdkConstantType.SERVICE_ACTION)
    public static final String CONTROLS_ACTION = "android.service.controls.ControlsProviderService";
    public static final String CALLBACK_BUNDLE = "CALLBACK_BUNDLE";
    public static final String CALLBACK_BINDER = "CALLBACK_BINDER";
    public static final String CALLBACK_TOKEN = "CALLBACK_TOKEN";

    public final String TAG = getClass().getSimpleName();

    private IControlsProviderCallback mCallback;
    private IBinder mToken;
    private RequestHandler mHandler;

    /**
     * Signal to retrieve all Controls. When complete, call
     * {@link IControlsProviderCallback#onLoad} to inform the caller.
     */
    public abstract void load();

    /**
     * Informs the service that the caller is listening for updates to the given controlIds.
     * {@link IControlsProviderCallback#onRefreshState} should be called any time
     * there are Control updates to render.
     */
    public abstract void subscribe(@NonNull List<String> controlIds);

    /**
     * Informs the service that the caller is done listening for updates,
     * and any calls to {@link IControlsProviderCallback#onRefreshState} will be ignored.
     */
    public abstract void unsubscribe();

    /**
     * The user has interacted with a Control. The action is dictated by the type of
     * {@link ControlAction} that was sent.
     */
    public abstract void onAction(@NonNull String controlId, @NonNull ControlAction action);

    /**
     * Sends a list of the controls available from this service.
     *
     * The items in the list must not have state information (as created by
     * {@link Control.StatelessBuilder}).
     * @param controls
     */
    public final void onLoad(@NonNull List<Control> controls) {
        Preconditions.checkNotNull(controls);
        List<Control> list = new ArrayList<>();
        for (Control control: controls) {
            if (control == null) {
                Log.e(TAG, "onLoad: null control.");
            }
            if (isStateless(control)) {
                list.add(control);
            } else {
                Log.w(TAG, "onLoad: control is not stateless.");
                list.add(new Control.StatelessBuilder(control).build());
            }
        }
        try {
            mCallback.onLoad(mToken, list);
        } catch (RemoteException ex) {
            ex.rethrowAsRuntimeException();
        }
    }

    /**
     * Sends a list of the controls requested by {@link ControlsProviderService#subscribe} with
     * their state.
     * @param statefulControls
     */
    public final void onRefreshState(@NonNull List<Control> statefulControls) {
        Preconditions.checkNotNull(statefulControls);
        try {
            mCallback.onRefreshState(mToken, statefulControls);
        } catch (RemoteException ex) {
            ex.rethrowAsRuntimeException();
        }
    }

    /**
     * Sends the response of a command in the specified {@link Control}.
     * @param controlId
     * @param response
     */
    public final void onControlActionResponse(
            @NonNull String controlId, @ControlAction.ResponseResult int response) {
        Preconditions.checkNotNull(controlId);
        if (!ControlAction.isValidResponse(response)) {
            Log.e(TAG, "Not valid response result: " + response);
            response = ControlAction.RESPONSE_UNKNOWN;
        }
        try {
            mCallback.onControlActionResponse(mToken, controlId, response);
        } catch (RemoteException ex) {
            ex.rethrowAsRuntimeException();
        }
    }

    private boolean isStateless(Control control) {
        return (control.getStatus() == Control.STATUS_UNKNOWN
                    && control.getControlTemplate().getTemplateType() == ControlTemplate.TYPE_NONE
                    && TextUtils.isEmpty(control.getStatusText()));
    }

    @Override
    public IBinder onBind(Intent intent) {
        mHandler = new RequestHandler(Looper.getMainLooper());

        Bundle bundle = intent.getBundleExtra(CALLBACK_BUNDLE);
        IBinder callbackBinder = bundle.getBinder(CALLBACK_BINDER);
        mToken = bundle.getBinder(CALLBACK_TOKEN);
        mCallback = IControlsProviderCallback.Stub.asInterface(callbackBinder);

        return new IControlsProvider.Stub() {
            public void load() {
                mHandler.sendEmptyMessage(RequestHandler.MSG_LOAD);
            }

            public void subscribe(List<String> ids) {
                mHandler.obtainMessage(RequestHandler.MSG_SUBSCRIBE, ids).sendToTarget();
            }

            public void unsubscribe() {
                mHandler.sendEmptyMessage(RequestHandler.MSG_UNSUBSCRIBE);
            }

            public void onAction(String id, ControlAction action) {
                ActionMessage msg = new ActionMessage(id, action);
                mHandler.obtainMessage(RequestHandler.MSG_ON_ACTION, msg).sendToTarget();
            }
        };
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mCallback = null;
        mHandler = null;
        return true;
    }

    private class RequestHandler extends Handler {
        private static final int MSG_LOAD = 1;
        private static final int MSG_SUBSCRIBE = 2;
        private static final int MSG_UNSUBSCRIBE = 3;
        private static final int MSG_ON_ACTION = 4;

        RequestHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_LOAD:
                    ControlsProviderService.this.load();
                    break;
                case MSG_SUBSCRIBE:
                    List<String> ids = (List<String>) msg.obj;
                    ControlsProviderService.this.subscribe(ids);
                    break;
                case MSG_UNSUBSCRIBE:
                    ControlsProviderService.this.unsubscribe();
                    break;
                case MSG_ON_ACTION:
                    ActionMessage aMsg = (ActionMessage) msg.obj;
                    ControlsProviderService.this.onAction(aMsg.mId, aMsg.mAction);
                    break;
            }
        }
    }

    private class ActionMessage {
        final String mId;
        final ControlAction mAction;

        ActionMessage(String id, ControlAction action) {
            this.mId = id;
            this.mAction = action;
        }
    }
}