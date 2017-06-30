package com.example.mylibrary;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.tencent.connect.common.Constants;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;
import com.unity3d.player.UnityPlayer;
import com.unity3d.player.UnityPlayerActivity;
import com.example.mylibrary.wxapi.WXApi;
import com.example.mylibrary.wxapi.WXEntryActivity;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.cookie.CookieJarImpl;
import com.zhy.http.okhttp.cookie.store.PersistentCookieStore;
import com.zhy.http.okhttp.https.HttpsUtils;
import com.zhy.http.okhttp.log.LoggerInterceptor;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public class MainActivity extends UnityPlayerActivity {
    public static MainActivity mInstance;
    public static Tencent mTencent;
    public static IWXAPI mWXapi;

    boolean isServerSideLogin = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i("MainActivity", "---->>onCreate");

        mInstance = this;
        mTencent = Tencent.createInstance(AppConst.AppID_QQ, this.getApplicationContext());

        mWXapi = WXEntryActivity.initWeiXin(this, AppConst.AppID_WX);

        initOkHttp();
    }

    //封装okhttp框架的初始化配置
    private void initOkHttp() {
        HttpsUtils.SSLParams sslParams = HttpsUtils.getSslSocketFactory(null, null, null);
        CookieJarImpl cookieJar = new CookieJarImpl(new PersistentCookieStore(getApplicationContext()));
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new LoggerInterceptor("TAG"))
                .cookieJar(cookieJar)
                .connectTimeout(20000L, TimeUnit.MILLISECONDS)
                .readTimeout(20000L, TimeUnit.MILLISECONDS)
                .writeTimeout(20000L, TimeUnit.MILLISECONDS)
                .sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager)
                //其他配置
                .build();
        OkHttpUtils.initClient(okHttpClient);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    public void ShowToast(final String msg) {
        UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(
                        UnityPlayer.currentActivity,
                        msg,
                        msg.length() > 50 ? Toast.LENGTH_LONG
                                : Toast.LENGTH_SHORT).show();
            }
        });
    }
    public void Init(String gameObject, String loginCallBack) {
        AppConst.gameObject = gameObject;
        AppConst.loginCallBack = loginCallBack;
    }

    //微信秘钥设置(登入前设置)
    public void SetWXAppSecret(String secret) {
        AppConst.AppSecret_WX = secret;
    }

    //微信登入
    public void LoginWX() {
        WXApi wxapi = WXApi.getWXApi();
        wxapi.loginWX();
    }


    //QQ登入
    public void LoginQQ() {
        ShowToast("LoginQQ 调用");
        mTencent.logout(this);
        if (!mTencent.isSessionValid()) {
            ShowToast("LoginQQ  客户端访问");
            mTencent.login(this, "all", loginListener);
            isServerSideLogin = false;
        }
    }

    //QQ登出
    public void LogoutQQ() {
        mTencent.logout(this);
    }

    private class BaseUiListener implements IUiListener {
        @Override
        public void onComplete(Object response) {
            if (null == response) {
                ShowToast("登录失败");
                return;
            }
            JSONObject jsonResponse = (JSONObject) response;
            if (null != jsonResponse && jsonResponse.length() == 0) {
                ShowToast("登录失败");
                return;
            }
            ShowToast("登录成功 : " + response);
            doComplete((JSONObject) response);
        }

        protected void doComplete(JSONObject values) {
        }

        @Override
        public void onError(UiError e) {
            ShowToast("QQ 登入异常");
        }

        @Override
        public void onCancel() {
            ShowToast("QQ 登入取消");
        }
    }

    IUiListener loginListener = new BaseUiListener() {
        @Override
        protected void doComplete(JSONObject values) {
            ShowToast("QQ 登入返回数据 :" + values);
            try {
                String token = values.getString(Constants.PARAM_ACCESS_TOKEN);
                String expires = values.getString(Constants.PARAM_EXPIRES_IN);
                String openId = values.getString(Constants.PARAM_OPEN_ID);
                if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(expires)
                        && !TextUtils.isEmpty(openId)) {
                    mTencent.setAccessToken(token, expires);
                    mTencent.setOpenId(openId);
                    UnityPlayer.UnitySendMessage(AppConst.gameObject, AppConst.loginCallBack, openId);
                }
            } catch (Exception e) {
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i("Tencent", "-->onActivityResult " + requestCode + " resultCode=" + resultCode);
        if (requestCode == Constants.REQUEST_LOGIN ||
                requestCode == Constants.REQUEST_APPBAR) {
            Tencent.onActivityResultData(requestCode, resultCode, data, loginListener);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
