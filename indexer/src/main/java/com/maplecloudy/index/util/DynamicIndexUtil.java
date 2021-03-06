package com.maplecloudy.index.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.solr.common.SolrInputDocument;

import com.google.common.collect.Maps;
import com.maplecloudy.index.constants.Constant;
import com.maplecloudy.indexer.HadoopIndexerBase;
import com.maplecloudy.indexer.solr.DynamicIndex;
import com.maplecloudy.indexer.solr.DynamicIndexConstant;
import com.maplecloudy.share.json.DateFormatStr;
import com.maplecloudy.share.json.JsonParse;

public class DynamicIndexUtil extends HadoopIndexerBase {
	private static Map<String, DateFormat> dfsCache = Maps.newHashMap();
	
	public static String getDefaultDinmicKey(Object obj) {
		String re = "";
		if (obj instanceof Integer) {
			re = Constant.INT;
		} else if (obj instanceof Double) {
			re = Constant.DOUBLE;
		} else if (obj instanceof Float) {
			re = Constant.FLOAT;
		} else if (obj instanceof Long) {
			re = Constant.LONG;
		} else if (obj instanceof Date) {
			re = Constant.DATE;
		} else if (obj instanceof Boolean) {
			re = Constant.BOOLEAN;
		} else if (obj instanceof String) {
			re = Constant.STRING;
		} else {
			re = Constant.STRING;
		}
		return re;
	}

	public static <T> SolrInputDocument genDynamicDoc(T t)
			throws IllegalArgumentException, IllegalAccessException,
			ParseException {
		SolrInputDocument doc = new SolrInputDocument();
		addFiledToDoc(doc, t);

		return doc;
	}

	@SuppressWarnings("rawtypes")
	private static void addFiledToDoc(SolrInputDocument doc, Object t)
			throws IllegalArgumentException, IllegalAccessException,
			ParseException {
		if (t == null)
			return;
		Map<String, Field> fields = JsonParse.getFields(t.getClass());
		for (Entry<String, Field> entry : fields.entrySet()) {
			Type type = entry.getValue().getGenericType();
			if (type instanceof ParameterizedType) {
				ParameterizedType ptype = (ParameterizedType) type;
				Class raw = (Class) ptype.getRawType();
				if (Collection.class.isAssignableFrom(raw)) { // array

					DynamicIndex di = entry.getValue().getAnnotation(
							DynamicIndex.class);
					if (di != null) {
						String name = di.value().replace("*",
								entry.getValue().getName());
						Collection<?> cl = (Collection<?>) entry.getValue()
								.get(t);
						if (null != cl) {
							for (Object o : cl) {
								HadoopIndexerBase.addSolrField(doc, name, o);
							}
						}
					}
				} else if (Map.class.isAssignableFrom(raw)) { // map
					DynamicIndex di = entry.getValue().getAnnotation(
							DynamicIndex.class);
					if (di != null) {
						Map<?, ?> cl = (Map<?, ?>) entry.getValue().get(t);
						if (null != cl) {
							for (Entry<?, ?> obj : cl.entrySet()) {
								String name = di.value().replace(
										"*",
										entry.getValue().getName() + "_"
												+ obj.getKey());
								HadoopIndexerBase.addSolrField(doc, name,
										obj.getValue());
							}
						}
					}

				} else {
					addDoc(doc, entry.getValue(), t);
				}
			} else if (type instanceof Class) {
				Class<?> c = (Class<?>) type;
				if (c.isPrimitive()
						|| // primitives
						c == Void.class || c == Boolean.class
						|| c == Integer.class || c == Long.class
						|| c == Float.class || c == Double.class
						|| c == Byte.class || c == Short.class
						|| c == Character.class || c == String.class) {
					addDoc(doc, entry.getValue(), t);
				} else {
					addFiledToDoc(doc, entry.getValue().get(t));
				}
			}
		}
	}

	private static void addDoc(SolrInputDocument doc, Field field, Object t)
			throws IllegalArgumentException, IllegalAccessException {
		field.setAccessible(true);
		Object value = field.get(t);
		if (null == value)
			return;
		// skip the invalid value
		if (DynamicIndexConstant.InvalidValue.contains(value))
			return;
		DynamicIndex di = field.getAnnotation(DynamicIndex.class);
		DateFormatStr dfs = field.getAnnotation(DateFormatStr.class);
		DateFormat sdf = null;
		if (dfs != null) {
			sdf = dfsCache.get(dfs);
			if (sdf == null) {
				sdf = new SimpleDateFormat(dfs.value());
				dfsCache.put(dfs.value(), sdf);
			}
		}
		if (di != null) {
		  String name = di.value().replace("*", field.getName());
		  if("caller_appid_s".equals(name))
		  {
			  System.out.println("ddd");
		  }
			if (sdf != null) {
				try {
					// Object value = field.get(t);
					if (value instanceof String) {// String format date
						HadoopIndexerBase.addSolrField(doc, name,
								sdf.parse(field.get(t).toString()));
					} else {// support long format date
						HadoopIndexerBase.addSolrField(doc, name, new Date(
								(Long) value));
					}
				} catch (ParseException e) {
					logger.info("already catch exception.... " + ExceptionUtils.getFullStackTrace(e));
					
				}
			} else
				HadoopIndexerBase.addSolrField(doc, name, field.get(t));
		}
	}
	
	 
}
