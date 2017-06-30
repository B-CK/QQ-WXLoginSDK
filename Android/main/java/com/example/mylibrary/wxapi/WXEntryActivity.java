package com.example.mylibrary.wxapi;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.modelmsg.SendAuth;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;
import com.example.mylibrary.MainActivity;

public class WXEntryActivity extends Activity implements IWXAPIEventHandler {
    private static final String TAG = "WXEntryActivity";
    public static WeChatCode mWeChatCode;
    public static WXEntryActivity mInstance;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "-->> onCreate");

        mInstance = this;

        try {
            MainActivity.mWXapi.handleIntent(getIntent(), this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //微信组件注册初始化
    public static IWXAPI initWeiXin(Context context, String appId) {
        Log.i(TAG, "-->> initWeiXin");
        if (TextUtils.isEmpty(appId)) {
            Toast.makeText(context.getApplicationContext(), "app_id 不能为空", Toast.LENGTH_SHORT).show();
        }
        IWXAPI api = WXAPIFactory.createWXAPI(context, appId, true);
        api.registerApp(appId);
        return api;
    }

    // 登录微信
    public static void loginWeixin(Context context, IWXAPI api, WeChatCode wechatCode) {
        Log.i(TAG, "-->> loginWeiXin");
        mWeChatCode = wechatCode;
        // 发送授权登录信息，来获取code
        SendAuth.Req req = new SendAuth.Req();
        // 应用的作用域，获取个人信息
        req.scope = "snsapi_userinfo";
        req.state = "login_state";
        api.sendReq(req);
    }

    // 微信发送请求到第三方应用时，会回调到该方法
    @Override
    public void onReq(BaseReq req) {
    }

    // 第三方应用发送到微信的请求处理后的响应结果，会回调到该方法
    @Override
    public void onResp(BaseResp resp) {
        switch (resp.errCode) {
            // 发送成功
            case BaseResp.ErrCode.ERR_OK:
                MainActivity.mInstance.ShowToast("第一步：请求CODE 成功");
                // 获取code
                String code = ((SendAuth.Resp) resp).code;
                mWeChatCode.getResponse(code);
                break;
            case BaseResp.ErrCode.ERR_USER_CANCEL:
                MainActivity.mInstance.ShowToast("onResp ERR_USER_CANCEL");
                //发送取消
                finish();
                break;
            case BaseResp.ErrCode.ERR_AUTH_DENIED:
                MainActivity.mInstance.ShowToast("onResp ERR_AUTH_DENIED");
                //发送被拒绝
                break;
            default:
                MainActivity.mInstance.ShowToast("onResp default errCode " + resp.errCode);
                //发送返回
                break;
        }
    }

    /**
     * 返回code的回调接口
     */
    public interface WeChatCode {
        void getResponse(String code);
    }
}
