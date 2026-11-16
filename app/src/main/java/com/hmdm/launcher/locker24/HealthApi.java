package com.hmdm.launcher.locker24;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface HealthApi {

    @POST("{project}/rest/public/plugin-locker24-health/report/{number}")
    Call<ResponseBody> report(@Path("project") String project,
                              @Path("number") String number,
                              @Header("X-Request-Signature") String sig,
                              @Body RequestBody body);

    final class Factory {
        private static final long TIMEOUT_SECONDS = 15;

        private static OkHttpClient client;
        private static String cachedBaseUrl;
        private static HealthApi cachedApi;

        private Factory() {
        }

        public static synchronized HealthApi get(String baseUrl) {
            if (cachedApi == null || !baseUrl.equals(cachedBaseUrl)) {
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                            .build();
                }
                cachedApi = new Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(client)
                        .build()
                        .create(HealthApi.class);
                cachedBaseUrl = baseUrl;
            }
            return cachedApi;
        }
    }
}
