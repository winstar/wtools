package com.eveow.wtools.http.test;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

/**
 * @author wangjianping
 */
public class PoolTest {

    public static void main(String[] args) {

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
                .<ConnectionSocketFactory>create()
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .register("http", PlainConnectionSocketFactory.getSocketFactory()).build();
        PoolingHttpClientConnectionManager clientConnectionManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry);
        clientConnectionManager.setMaxTotal(50);
        clientConnectionManager.setDefaultMaxPerRoute(8);

        CloseableHttpClient httpclient = HttpClients.custom()
                .setConnectionManager(clientConnectionManager).build();


        for (int i = 0; i < 20; i++) {
            new Thread(() -> {
                try {
                    CloseableHttpResponse response = httpclient.execute(new HttpGet("http://localhost:8903/sleep?second=3"));
                    HttpEntity entity = response.getEntity();
                    try {
                        if (entity != null) {
                            System.out.println(EntityUtils.toString(entity));
                        }
                    } finally {
                        if (entity != null) {
                            entity.getContent().close();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }

        for (int i = 0; i < 10; i++) {
            System.out.println(clientConnectionManager.getTotalStats().toString());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
