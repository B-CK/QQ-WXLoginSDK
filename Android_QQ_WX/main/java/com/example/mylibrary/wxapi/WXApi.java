package com.example.mylibrary.wxapi;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.google.gson.Gson;
import com.unity3d.player.UnityPlayer;
import com.example.mylibrary.AppConst;
import com.example.mylibrary.MainActivity;
import com.example.mylibrary.ShareUtils;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import okhttp3.Call;

public class WXApi {

    private static final String WEIXIN_ACCESS_TOKEN_KEY = "wx_access_token_key";
    private static final String WEIXIN_OPENID_KEY = "wx_openid_key";
    private static final String WEIXIN_REFRESH_TOKEN_KEY = "wx_refresh_token_key";
    private static WXApi wxapi = null;

    private Gson mGson = new Gson();

    public static WXApi getWXApi()
    {
        if (wxapi ==null)
            wxapi = new WXApi();
        return wxapi;
    }


    /**
     * 验证是否成功
     *
     * @param response 返回消息
     * @return 是否成功
     */
    private boolean validateSuccess(String response) {
        MainActivity.mInstance.ShowToast("验证返回数据是否错误: " + response);
        return response.indexOf("errcode") < 0 || (response.indexOf("errcode") >= 0 && response.indexOf("ok") >= 0);
    }

    private boolean checkApkExist(Context context, String packageName) {
        if (packageName == null || "".equals(packageName))
            return false;
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(packageName,
                            PackageManager.GET_UNINSTALLED_PACKAGES);
            MainActivity.mInstance.ShowToast("存在: "+info.toString());
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            MainActivity.mInstance.ShowToast("不存在");
            return false;
        }
    }



    //微信登录  unity中调用该方法
    public void loginWX() {
        if (!checkApkExist(MainActivity.mInstance, "com.tencent.mm")) {
            UnityPlayer.currentActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            UnityPlayer.currentActivity,
                            "请先安装微信",
                            "请先安装微信".length() > 50 ? Toast.LENGTH_LONG
                                    : Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        WXEntryActivity.loginWeixin(MainActivity.mInstance, MainActivity.mWXapi, new WXEntryActivity.WeChatCode() {
            @Override
            public void getResponse(String code) {
                // 无access_token
                getAccessToken(code);
                WXEntryActivity.mInstance.finish();
            }
        });
    }

    /**
     * 微信登录获取授权口令
     */
    private void getAccessToken(String code) {
        String url = "https://api.weixin.qq.com/sns/oauth2/access_token?" +
                "appid=" + AppConst.AppID_WX +
                "&secret=" + AppConst.AppSecret_WX +
                "&code=" + code +
                "&grant_type=authorization_code";
        // 网络请求获取access_token
        OkHttpUtils.get().url(url).build().execute(new StringCallback() {
            @Override
            public void onError(Call call, Exception e, int id) {
                MainActivity.mInstance.ShowToast("getAccessToken 异常  : " + e.getMessage());
            }

            @Override
            public void onResponse(String response, int id) {
                MainActivity.mInstance.ShowToast("getAccessToken数据  : " + response);
                // 判断是否获取成功，成功则去获取用户信息，否则提示失败
                processGetAccessTokenResult(response);
            }
        });
    }

    /**
     * 微信登录处理获取的授权信息结果
     *
     * @param response 授权信息结果
     */
    private void processGetAccessTokenResult(String response) {
        if (validateSuccess(response)) {
            MainActivity.mInstance.ShowToast("第二步：获取access_token 成功");
            WXAccessTokenInfo tokenInfo = mGson.fromJson(response, WXAccessTokenInfo.class);
            //存储数据access_token,openid,refresh_token
            //验证AccessToken是否过期
            isExpireAccessToken(tokenInfo.getAccess_token(), tokenInfo.getOpenid(), tokenInfo.getRefresh_token());
        } else {
            MainActivity.mInstance.ShowToast("返回数据错误 : " + response);
        }
    }

    /**
     * 微信登录判断accesstoken是否过期
     *
     * @param accessToken token
     * @param openid      授权用户唯一标识
     */
    private void isExpireAccessToken(final String accessToken, final String openid, final String refreshToken) {
        String url = "https://api.weixin.qq.com/sns/auth?" +
                "access_token=" + accessToken +
                "&openid=" + openid;
        OkHttpUtils.get().url(url).build().execute(new StringCallback() {
            @Override
            public void onError(Call call, Exception e, int id) {
                MainActivity.mInstance.ShowToast("验证accessToken的有效性 异常");
            }

            @Override
            public void onResponse(String response, int id) {
                if (validateSuccess(response)) {
                    MainActivity.mInstance.ShowToast("第三步： accessToken未过期 -> 获取用户信息");
                    // accessToken没有过期，获取用户信息
                    getUserInfo(accessToken, openid);
                } else {
                    MainActivity.mInstance.ShowToast("第三步： accessToken已过期 -> 刷新用户信息");
                    // 过期了，使用refresh_token来刷新accesstoken
                    refreshAccessToken(refreshToken);
                }
            }
        });
    }

    /**
     * 微信登录刷新获取新的access_token
     */
    private void refreshAccessToken(final String refreshToken) {
        // 拼装刷新access_token的url请求地址
        String url = "https://api.weixin.qq.com/sns/oauth2/refresh_token?" +
                "appid=" + AppConst.AppID_WX +
                "&grant_type=refresh_token" +
                "&refresh_token=" + refreshToken;
        // 请求执行
        OkHttpUtils.get().url(url).build().execute(new StringCallback() {
            @Override
            public void onError(Call call, Exception e, int id) {
                MainActivity.mInstance.ShowToast("access_token刷新失败！！！");
                // 重新请求授权
                loginWX();
            }

            @Override
            public void onResponse(String response, int id) {
                // 判断是否获取成功，成功则去获取用户信息，否则提示失败
                MainActivity.mInstance.ShowToast("access_token刷新成功");
                processGetAccessTokenResult(response);
            }
        });
    }

    /**
     * 微信token验证成功后，联网获取用户信息
     *
     * @param access_token
     * @param openid
     */
    private void getUserInfo(String access_token, String openid) {
        String url = "https://api.weixin.qq.com/sns/userinfo?" +
                "access_token=" + access_token +
                "&openid=" + openid;
        OkHttpUtils.get().url(url).build().execute(new StringCallback() {
            @Override
            public void onError(Call call, Exception e, int id) {
                MainActivity.mInstance.ShowToast("获取用户信息失败 :" + e.getMessage());
            }

            @Override
            public void onResponse(String response, int id) {
                MainActivity.mInstance.ShowToast("获取用户信息String是：" + response);
                // 解析获取的用户信息
                Gson gson = new Gson();
                WXUserInfo userInfo = gson.fromJson(response, WXUserInfo.class);
                UnityPlayer.UnitySendMessage(AppConst.gameObject, AppConst.loginCallBack, userInfo.getOpenid());
            }
        });
    }

}
