package com.c9mj.platform.util.retrofit;

import com.blankj.utilcode.utils.NetworkUtils;
import com.c9mj.platform.MyApplication;
import com.c9mj.platform.live.mvp.model.LiveBaseBean;
import com.c9mj.platform.util.retrofit.exception.RetrofitException;
import com.jakewharton.retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import org.reactivestreams.Publisher;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.FlowableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * author: LMJ
 * date: 2016/9/1
 */
public class RetrofitHelper {

    public static final String BASE_EXPLORE_URL = "http://c.m.163.com";
    public static final String BASE_LIVE_URL = "http://api.maxjia.com";
    public static final String BASE_USER_URL = "http://api.douban.com/v2/movie/";
    public static final String BASE_PANDA_URL = "http://www.panda.tv";

    private static Retrofit explore = null;
    private static Retrofit live = null;
    private static Retrofit user = null;
    private static Retrofit panda = null;

    private static final Interceptor REWRITE_CACHE_CONTROL_INTERCEPTOR = new Interceptor() {
        @Override
        public Response intercept(Chain chain) throws IOException {

            Request request = chain.request();
            if (!NetworkUtils.isAvailable(MyApplication.getContext())) {
                CacheControl cacheControl = new CacheControl.Builder()
                        .maxStale(30, TimeUnit.SECONDS )
                        .build();
                request = request.newBuilder()
                        .cacheControl(CacheControl.FORCE_CACHE)
                        .build();
            }

            Response response = chain.proceed(request);

            if (NetworkUtils.isAvailable(MyApplication.getContext())){
                /**
                 * If you have problems in testing on which side is problem (server or app).
                 * You can use such feauture to set headers received from server.
                 */
                int maxAge = 60 * 60; // 有网络时,设置缓存超时时间1个小时
                response =  response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=" + maxAge)//设置缓存超时时间
                        .build();
            }else {
                int maxStale = 60 * 60 * 24 * 28;//无网络时，设置超时为4周
                response = response.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, only-if-cached, max-stale=" + maxStale)
                        .build();
            }
            return response;
        }
    };

    public static Retrofit getExploreHelper() {
        if (explore == null){
            try {
                synchronized (RetrofitHelper.class){
                    if (explore == null) {
                        File httpCacheDirectory = new File(MyApplication.getContext().getCacheDir(), "exploreCache");
                        Cache cache = new Cache(httpCacheDirectory, 10 * 1024 * 1024);//缓存10MB
                        OkHttpClient.Builder httpBuidler = new OkHttpClient().newBuilder();
                        httpBuidler.cache(cache)
                                .connectTimeout(5, TimeUnit.SECONDS);//连接超时限制5秒
                                //添加拦截器
//                                .addNetworkInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR)//离线缓存
//                                .addInterceptor(REWRITE_CACHE_CONTROL_INTERCEPTOR);

                        explore = new Retrofit.Builder()
                                .client(httpBuidler.build())
                                .baseUrl(BASE_EXPLORE_URL)
                                .addConverterFactory(GsonConverterFactory.create())
                                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                                .build();
                    }
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return explore;
    }

    public static Retrofit getLiveHelper() {
        if (live == null){
            synchronized (RetrofitHelper.class){
                if (live == null) {
                    live = new Retrofit.Builder()
                            .baseUrl(BASE_LIVE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                            .build();
                }
            }
        }
        return live;
    }

    public static Retrofit getUserHelper() {
        if (user == null){
            synchronized (RetrofitHelper.class){
                user = new Retrofit.Builder()
                        .client(new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build())
                        .baseUrl(BASE_USER_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .build();
            }
        }
        return user;
    }

    public static Retrofit getPandaHelper() {
        if (panda == null){
            synchronized (RetrofitHelper.class){
                panda = new Retrofit.Builder()
                        .client(new OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build())
                        .baseUrl(BASE_PANDA_URL)
                        .addConverterFactory(GsonConverterFactory.create())
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .build();
            }
        }
        return panda;
    }

    public static <T> FlowableTransformer<LiveBaseBean<T>, T> handleLiveResult(){
        return new FlowableTransformer<LiveBaseBean<T>, T>() {
            @Override
            public Publisher<T> apply(final Flowable<LiveBaseBean<T>> upstream) {
                return upstream.flatMap(new Function<LiveBaseBean<T>, Publisher<T>>() {
                    @Override
                    public Publisher<T> apply(final LiveBaseBean<T> baseBean) throws Exception {
                        return new Publisher<T>() {
                            @Override
                            public void subscribe(org.reactivestreams.Subscriber<? super T> subscriber) {
                                if (baseBean.getStatus().equals("ok")){
                                    subscriber.onNext(baseBean.getResult());
                                }else {
                                    subscriber.onError(new RetrofitException(baseBean.getMsg()));
                                }
                            }
                        };
                    }
                }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
            }
        };
    }
}
