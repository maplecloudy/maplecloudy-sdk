package com.maplecloudy.spider.protocol.httpmethod;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.http.HttpHost;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

public class InfoToEs {
  
  private static String ES_IP = "es1.ali.szol.bds.com";
  private final static int ES_PORT = 9200;
  
  private final static String ES_INDEX_HTTP_REEOE = "http_error";
  private final static String ES_INDEX_HTTP_RESPONSE = "http_response";
  private final static String ES_INDEX_PARSE_REEOE = "parse_error";
  private final static String ES_INDEX_PARSE_RESPONSE = "parse_response";
  private final static String ES_INDEX_URL_TYPE = "url_type";
  
  private final static String ES_TYPE = "_doc";
  
  private final static int BULK_SIZE = 500;
  
  private final static Object OBJECT = new Object();
  private final static Gson gson = new Gson();
  private static List<String> httpErrorList = Lists.newArrayList();
  private static List<String> httpResponseList = Lists.newArrayList();
  private static List<String> parseErrorList = Lists.newArrayList();
  private static List<String> parseResponseList = Lists.newArrayList();
  private static List<String> urlTypeList = Lists.newArrayList();
  
  private static boolean flag = false;
  private final static JSONObject json = new JSONObject();
  private final static StringBuffer sb = new StringBuffer();
  private final static List<String> listKey = Lists.newArrayList();
  
  private RestHighLevelClient client;
  private static InfoToEs infoToEs;
  
  private InfoToEs() {}
  
  private InfoToEs(Configuration conf) {}
  
  public static InfoToEs getInstance() {
    if (infoToEs != null) return infoToEs;
    synchronized (OBJECT) {
      if (infoToEs == null) {
        infoToEs = new InfoToEs();
        infoToEs.client = new RestHighLevelClient(
            RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
      }
    }
    return infoToEs;
  }
  
  public static InfoToEs getInstance(Configuration conf) {
    if (infoToEs != null) return infoToEs;
    synchronized (OBJECT) {
      if (infoToEs == null) {
        infoToEs = new InfoToEs(conf);
        infoToEs.client = new RestHighLevelClient(
            RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
      }
    }
    return infoToEs;
  }
  
  private static String HTTP_ERROR_MODEL = "{\"url\":\"%s\",\"code\":%s,\"error\":\"%s\",\"time\":%s}";
  
  public synchronized void addHttpError(String url, int code, Exception e) {
    
    StackTraceElement[] exceptionStack = e.getStackTrace();
    sb.append(e.getMessage());
    for (StackTraceElement ste : exceptionStack) {
      sb.append("     @      " + ste);
    }
    try {
      json.put("url", url);
      json.put("code", code);
      json.put("error", sb.toString());
      json.put("time", System.currentTimeMillis());
      httpErrorList.add(json.toString());
    } catch (JSONException e2) {
      e2.printStackTrace();
    } finally {
      cleanData();
    }
    if (httpErrorList.size() >= BULK_SIZE) {
      if (client == null) client = new RestHighLevelClient(
          RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
      BulkRequest request = new BulkRequest();
      for (String error : httpErrorList) {
        request.add(new IndexRequest(ES_INDEX_HTTP_REEOE, ES_TYPE).source(error,
            XContentType.JSON));
      }
      try {
        client.bulk(request, RequestOptions.DEFAULT);
      } catch (Exception e1) {
        e1.printStackTrace();
      } finally {
        httpErrorList.clear();
//				closeClient();
      }
    }
  }
  
  private static String PARSE_ERROR_MODEL = "{\"url\":\"%s\",\"error\":\"%s\",\"time\":%s}";
  
  public synchronized void addParseError(String url, Exception e) {
    StackTraceElement[] exceptionStack = e.getStackTrace();
    sb.append(e.getMessage());
    for (StackTraceElement ste : exceptionStack) {
      sb.append("     @     " + ste);
    }
    try {
      json.put("url", url);
      json.put("error", sb.toString());
      json.put("time", System.currentTimeMillis());
      httpErrorList.add(json.toString());
    } catch (JSONException e2) {
      e2.printStackTrace();
    } finally {
      cleanData();
    }
    parseErrorList.add(json.toString());
    if (parseErrorList.size() >= BULK_SIZE) {
      if (client == null) client = new RestHighLevelClient(
          RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
      BulkRequest request = new BulkRequest();
      for (String error : parseErrorList) {
        request.add(new IndexRequest(ES_INDEX_PARSE_REEOE, ES_TYPE)
            .source(error, XContentType.JSON));
      }
      try {
        client.bulk(request, RequestOptions.DEFAULT);
      } catch (Exception e1) {
        e1.printStackTrace();
      } finally {
        parseErrorList.clear();
//				closeClient();
      }
    }
  }
  
  private static String HTTP_RESPONSE_MODEL = "{\"url\":\"%s\",\"code\":%s,\"response\":%s,\"time\":%s}";
  
  public synchronized void addHttpResponse(String url, int code,
      String response) {
    try {
      json.put("url", url);
      json.put("code", code);
      json.put("response", response);
      json.put("time", System.currentTimeMillis());
      httpErrorList.add(json.toString());
    } catch (JSONException e2) {
      e2.printStackTrace();
    } finally {
      cleanData();
    }
    httpResponseList.add(json.toString());
    if (httpResponseList.size() >= BULK_SIZE / 2) {
      if (client == null) client = new RestHighLevelClient(
          RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
      BulkRequest request = new BulkRequest();
      for (String info : httpResponseList) {
        request.add(new IndexRequest(ES_INDEX_HTTP_RESPONSE, ES_TYPE)
            .source(info, XContentType.JSON));
      }
      try {
        client.bulk(request, RequestOptions.DEFAULT);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        httpResponseList.clear();
//				closeClient();
      }
    }
  }
  
  private static String PARSE_RESPONSE_MODEL = "{\"url\":\"%s\",\"response\":%s,\"time\":%s}";
  
  public synchronized void addParseResponse(String url, List<Object> response) {
    try {
      json.put("url", url);
      json.put("response", gson.toJson(response));
      json.put("time", System.currentTimeMillis());
      httpErrorList.add(json.toString());
    } catch (JSONException e2) {
      e2.printStackTrace();
    } finally {
      cleanData();
    }
    parseResponseList.add(json.toString());
    if (parseResponseList.size() >= BULK_SIZE) {
      if (client == null) client = new RestHighLevelClient(
          RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
      BulkRequest request = new BulkRequest();
      for (String info : parseResponseList) {
        request.add(new IndexRequest(ES_INDEX_PARSE_RESPONSE, ES_TYPE)
            .source(info, XContentType.JSON));
      }
      try {
        client.bulk(request, RequestOptions.DEFAULT);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        parseResponseList.clear();
//				closeClient();
      }
    }
  }
  
  private interface UrlTypeModel {
    String url = "url";
    String web = "web";
    String type = "type";
    String retry = "retry";
    String parse = "parse";
    
  }
  
  private static String UTL_TYPE_MODEL = "{\"url\":\"%s\",\"web\":\"%s\",\"type\":\"%s\",\"retry\":%s,\"parse\":\"%s\"}";
  
  public synchronized void addUrlType(String url, String web, String type,
      String parse) {
    urlTypeList.add(String.format(UTL_TYPE_MODEL, url, web, type, 1, parse));
    if (urlTypeList.size() >= BULK_SIZE) {
      if (client == null) client = new RestHighLevelClient(
          RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
      BulkRequest request = new BulkRequest();
      for (String info : urlTypeList) {
        Map<String,Object> parameters = Maps.newHashMap();
        parameters.put(UrlTypeModel.retry, 1);
        Script inline = new Script(ScriptType.INLINE, "painless", "ctx._source."
            + UrlTypeModel.retry + " += params." + UrlTypeModel.retry,
            parameters);
        request.add(new UpdateRequest(ES_INDEX_URL_TYPE, ES_TYPE,
            info.split("url\":\"")[1].split("\",\"web")[0])
                .upsert(info, XContentType.JSON)
                .id(info.split("url\":\"")[1].split("\",\"web")[0])
                .script(inline));
      }
      try {
        client.bulk(request, RequestOptions.DEFAULT);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        urlTypeList.clear();
//				closeClient();
      }
    }
  }
  
  public synchronized void cleanUp() {
    if (client == null) return;
    if (flag) return;
//    client = new RestHighLevelClient(
//        RestClient.builder(new HttpHost(ES_IP, ES_PORT, "http")));
    BulkRequest request = new BulkRequest();
    for (String info : parseResponseList) {
      request.add(new IndexRequest(ES_INDEX_PARSE_RESPONSE, ES_TYPE)
          .source(info, XContentType.JSON));
    }
    for (String info : httpResponseList) {
      request.add(new IndexRequest(ES_INDEX_HTTP_RESPONSE, ES_TYPE).source(info,
          XContentType.JSON));
    }
    for (String error : parseErrorList) {
      request.add(new IndexRequest(ES_INDEX_PARSE_REEOE, ES_TYPE).source(error,
          XContentType.JSON));
    }
    for (String error : httpErrorList) {
      request.add(new IndexRequest(ES_INDEX_HTTP_REEOE, ES_TYPE).source(error,
          XContentType.JSON));
    }
    for (String info : urlTypeList) {
      Map<String,Object> parameters = Maps.newHashMap();
      parameters.put(UrlTypeModel.retry, 1);
      Script inline = new Script(ScriptType.INLINE, "painless", "ctx._source."
          + UrlTypeModel.retry + " += params." + UrlTypeModel.retry,
          parameters);
      request.add(new UpdateRequest(ES_INDEX_URL_TYPE, ES_TYPE,
          info.split("url\":\"")[1].split("\",\"web")[0])
              .upsert(info, XContentType.JSON)
              .id(info.split("url\":\"")[1].split("\",\"web")[0])
              .script(inline));
    }
    try {
      client.bulk(request, RequestOptions.DEFAULT);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      parseResponseList.clear();
      httpResponseList.clear();
      parseErrorList.clear();
      httpErrorList.clear();
      closeClient();
      flag = true;
    }
  }
  
  private void closeClient() {
    try {
      client.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  private void cleanData() {
    Iterator keys = json.keys();
    
    while (keys.hasNext()) {
      listKey.add((String) keys.next());
    }
    Iterator<String> itKey = listKey.iterator();
    while (itKey.hasNext()) {
      json.remove(itKey.next());
    }
    if (sb.length() >= 1) {
      sb.delete(0, sb.length() - 1);
    }
  }
  
}
