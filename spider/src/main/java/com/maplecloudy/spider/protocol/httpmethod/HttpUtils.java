package com.maplecloudy.spider.protocol.httpmethod;

import java.lang.invoke.MethodHandles;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.http.HttpHost;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.maplecloudy.spider.crawl.CrawlDatum;
import com.maplecloudy.spider.protocol.Content;
import com.maplecloudy.spider.protocol.HttpParameters;
import com.maplecloudy.spider.protocol.Protocol;
import com.maplecloudy.spider.protocol.ProtocolOutput;
import com.maplecloudy.spider.protocol.ProtocolStatus;

public class HttpUtils implements Protocol {
  // bind ip proxy
  static {
    String proxyUsernm = "maplecloudy";
    String proxyPasswd = "maplecloudy";
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(proxyUsernm,
            proxyPasswd.toCharArray());
      }
    });
  }
  
  private static final Logger LOG = LoggerFactory
      .getLogger(MethodHandles.lookup().lookupClass());
  
  private final static int TIME_OUT = 10 * 1000;
  private final static int MAX_SIZE = 10 * 1024 * 1024;
  
  private static CloseableHttpClient httpClient = HttpClients.createDefault();
  private static RequestConfig.Builder builder = RequestConfig.custom()
      .setSocketTimeout(TIME_OUT).setConnectTimeout(TIME_OUT)
      .setConnectionRequestTimeout(TIME_OUT)
      .setCookieSpec(CookieSpecs.IGNORE_COOKIES);
  
  public static boolean ES_ABLE = true;
  
  private static final byte[] EMPTY_CONTENT = new byte[0];
  private Configuration conf = null;
  private static HttpUtils httpUtils;
  private final static Object OBJECT = new Object();
  
  private HttpUtils() {}
  
  public static HttpUtils getInstance() {
    if (httpUtils != null) return httpUtils;
    synchronized (OBJECT) {
      if (httpUtils == null) {
        httpUtils = new HttpUtils();
      }
    }
    return httpUtils;
  }
  
  @Override
  public Configuration getConf() {
    // TODO Auto-generated method stub
    return conf;
  }
  
  @Override
  public void setConf(Configuration arg0) {
    this.conf = arg0;
  }
  
  @Override
  public ProtocolOutput getProtocolOutput(String url, CrawlDatum datum) {
    // TODO Auto-generated method stub
    Map<String,String> map = datum.getExtendData();
    String web = map.containsKey("web") ? map.get("web") : "DEFAULT";
    String type = map.containsKey("type") ? map.get("type") : "DEFAULT";
    String urlType = map.containsKey("urlType") ? map.get("urlType")
        : "DEFAULT";
    String pageNum = map.containsKey("pageNum") ? map.get("pageNum") : "1";
    String deepth = map.containsKey("deepth") ? map.get("deepth") : "0";
    try {
      HttpParameters parm = new HttpParameters(datum.getExtendData());
      String ip2port = ProxyWithEs.getInstance().getProxy();
      
      Content c;
      int code;
      String html = "";
      if ("http".equals(parm.getMethod())) {
        HttpRequestBase http = null;
        if ("post".equals(parm.getType())) {
          http = new HttpPost(url);
        } else {
          http = new HttpGet(url);
        }
        if (parm.getAccept() != null)
          http.addHeader("Accept", parm.getAccept());
        if (parm.getAccept_encoding() != null)
          http.addHeader("Accept-Encoding", parm.getAccept_encoding());
        if (parm.getAccept_language() != null)
          http.addHeader("Accept-Language", parm.getAccept_language());
        if (parm.getCookie() != null)
          http.addHeader("Cookie", parm.getCookie());
        if (parm.getX_requested_with() != null)
          http.addHeader("x_requested_with", parm.getX_requested_with());
        if (parm.getContentType() != null)
          http.addHeader("Content-Type", parm.getContentType());
        if (parm.getRefer() != null) http.addHeader("Referer", parm.getRefer());
        if (ip2port != null && ip2port.contains(":")) {
          String[] proxy = ip2port.split(":");
          http.setConfig(builder
              .setProxy(new HttpHost(proxy[0], Integer.valueOf(proxy[1])))
              .build());
        }
        CloseableHttpResponse response;
        try {
          response = httpClient.execute(http);
        } catch (Exception e) {
          LOG.error("fetch proxy -- url " + url + " error ", e);
          if (ES_ABLE) InfoToEs.getInstance().addHttpError(url, 900, web, type,
              urlType, pageNum, deepth, e);
          http.setConfig(builder.build());
          response = httpClient.execute(http);
        }
        code = response.getStatusLine().getStatusCode();
        String chareset = "utf-8";
        if (parm.getCharset() != null) chareset = parm.getCharset();
        html = EntityUtils.toString(response.getEntity(), chareset);
        response.close();
        byte[] content = html.getBytes();
        c = new Content(url, (content == null ? EMPTY_CONTENT : content),
            Maps.newHashMap());
        c.setExtendData(datum.getExtendData());
        c = new Content(url, (EMPTY_CONTENT), Maps.newHashMap());
      } else {
        Connection connection = Jsoup.connect(url).timeout(TIME_OUT)
            .maxBodySize(MAX_SIZE).ignoreContentType(true)
            .userAgent(UserAgent.getAgent());
        if (parm.getAccept() != null)
          connection.header("Accept", parm.getAccept());
        if (parm.getAccept_encoding() != null)
          connection.header("Accept-Encoding", parm.getAccept_encoding());
        if (parm.getAccept_language() != null)
          connection.header("Accept-Language", parm.getAccept_language());
        if (parm.getCookie() != null)
          connection.header("Cookie", parm.getCookie());
        if (parm.getX_requested_with() != null)
          connection.header("x_requested_with", parm.getX_requested_with());
        if (parm.getContentType() != null)
          connection.header("Content-Type", parm.getContentType());
        if (parm.getRefer() != null)
          connection.header("Referer", parm.getRefer());
//        if (ip2port != null && ip2port.contains(":")) {
//          String[] proxy = ip2port.split(":");
//          connection.proxy(new Proxy(Proxy.Type.SOCKS, InetSocketAddress
//              .createUnresolved(proxy[0], Integer.valueOf(proxy[1]))));
//        }
        
        if ("post".equals(parm.getType())) {
          connection.method(Method.POST);
          if (parm.getRequestBody() != null)
            connection.requestBody(parm.getRequestBody());
        } else {
          connection = connection.method(Method.GET);
        }
        Connection.Response response;
        
        if (ip2port != null && ip2port.contains(":")) {
          String[] proxy = ip2port.split(":");
          connection.proxy(new Proxy(Proxy.Type.SOCKS, InetSocketAddress
              .createUnresolved(proxy[0], Integer.valueOf(proxy[1]))));
          try {
            response = connection.execute();
          } catch (Exception e) {
            LOG.error("fetch proxy -- url " + url + " error ", e);
            if (ES_ABLE) InfoToEs.getInstance().addHttpError(url, 901, web,
                type, urlType, pageNum, deepth, e);
            connection.proxy(null);
            response = connection.execute();
          }
        } else {
          System.out.println("使用本地ip进行访问");
          response = connection.execute();
        }
        code = response.statusCode();
        html = response.body();
        byte[] content = response.bodyAsBytes();
        c = new Content(url, (content == null ? EMPTY_CONTENT : content),
            Maps.newHashMap());
        c.setExtendData(datum.getExtendData());
      }
      if (ES_ABLE) InfoToEs.getInstance().addHttpResponse(url, code, web, type,
          urlType, pageNum, deepth, html);
      System.out.println("抓取网页的状态码：" + code);
      if (code == 200) { // got a good response
        return new ProtocolOutput(c); // return it
        
      } else if (code == 410) { // page is gone
        return new ProtocolOutput(c, new ProtocolStatus(ProtocolStatus.GONE,
            "Http: " + code + " url=" + url));
        
      } else if (code == 400) { // bad request, mark as GONE
        if (LOG.isTraceEnabled()) {
          LOG.trace("400 Bad request: " + url);
        }
        return new ProtocolOutput(c,
            new ProtocolStatus(ProtocolStatus.GONE, url));
      } else if (code == 401) { // requires authorization, but no valid
        // auth provided.
        if (LOG.isTraceEnabled()) {
          LOG.trace("401 Authentication Required");
        }
        return new ProtocolOutput(c, new ProtocolStatus(
            ProtocolStatus.ACCESS_DENIED, "Authentication required: " + url));
      } else if (code == 404) {
        return new ProtocolOutput(c,
            new ProtocolStatus(ProtocolStatus.NOTFOUND, url));
      } else if (code == 410) { // permanently GONE
        return new ProtocolOutput(c,
            new ProtocolStatus(ProtocolStatus.GONE, url));
      } else {
        return new ProtocolOutput(c, new ProtocolStatus(
            ProtocolStatus.EXCEPTION, "Http code=" + code + ", url=" + url));
      }
    } catch (Exception e) {
      LOG.error("fetch -- url " + url + " error ", e);
      if (ES_ABLE) InfoToEs.getInstance().addHttpError(url, 0, web, type,
          urlType, pageNum, deepth, e);
      return new ProtocolOutput(null, new ProtocolStatus(e));
    }
  }
  
  @Override
  public ProtocolOutput getProtocolOutput(String url) {
    return getProtocolOutput(url, new CrawlDatum());
  }
  
  public class Model {
    String string;
  }
  
  public static void main(String[] args) throws Exception {
    InfoToEs.getInstance(new Configuration()).initClient();
    for (int i = 0; i < 600; i++) {
      InfoToEs.getInstance().addHttpError("dde", 1, "2", "3", "4", "5", "6",
          new Exception("e"));
    }
    InfoToEs.getInstance().cleanUp();
  }
  
}
