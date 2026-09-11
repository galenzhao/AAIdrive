package com.autonavi.base.ae.gmap.glyph;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Drop-in replacement for the AMap SDK ReflectUtil.
 *
 * GlyphLoader reflects {@code AccessibilityManager.isHighTextContrastEnabled} on
 * every raster. That method is missing on many devices, so the SDK prints a full
 * stack to System.err and then logs {@code HighText}. Returning false here keeps
 * the map renderer working without the per-glyph spam.
 */
public final class ReflectUtil {
	public ReflectUtil() {
	}

	public static Object newInstance(String className, Class<?>[] argTypes, Object[] args) {
		requireClassName(className);
		try {
			Class<?> clazz = Class.forName(className);
			Constructor<?> constructor = argTypes != null
					? clazz.getDeclaredConstructor(argTypes)
					: clazz.getDeclaredConstructor();
			constructor.setAccessible(true);
			return argTypes != null ? constructor.newInstance(args) : constructor.newInstance();
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	public static Object getInstance(String className, Object... args) {
		requireClassName(className);
		try {
			Class<?> clazz = Class.forName(className);
			if (args != null) {
				Class<?>[] argTypes = new Class<?>[args.length];
				for (int i = 0; i < args.length; i++) {
					argTypes[i] = args[i].getClass();
				}
				Constructor<?> constructor = clazz.getDeclaredConstructor(argTypes);
				constructor.setAccessible(true);
				return constructor.newInstance(args);
			}
			Constructor<?> constructor = clazz.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	public static Object invoke(String className, Object target, String methodName, Object... args) {
		requireClassName(className);
		requireMethodName(methodName);
		if ("isHighTextContrastEnabled".equals(methodName)) {
			return Boolean.FALSE;
		}
		try {
			Class<?> clazz = Class.forName(className);
			if (args != null) {
				Class<?>[] argTypes = new Class<?>[args.length];
				for (int i = 0; i < args.length; i++) {
					argTypes[i] = args[i].getClass();
				}
				Method method = clazz.getDeclaredMethod(methodName, argTypes);
				method.setAccessible(true);
				return method.invoke(target, args);
			}
			Method method = clazz.getDeclaredMethod(methodName);
			method.setAccessible(true);
			return method.invoke(target);
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	public static Object invokeMethod(Object target, Method method, Object... args) {
		if (method == null) {
			throw new IllegalArgumentException("method 不能为空");
		}
		try {
			method.setAccessible(true);
			return method.invoke(target, args);
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	public static Object getField(String className, Object target, String fieldName) {
		requireClassName(className);
		requireFieldName(fieldName);
		try {
			Field field = Class.forName(className).getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(target);
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	public static void setField(String className, Object target, String fieldName, Object value) {
		requireClassName(className);
		requireFieldName(fieldName);
		try {
			Field field = Class.forName(className).getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static Method getMethod(String className, String methodName, Class<?>... argTypes) {
		requireClassName(className);
		requireMethodName(methodName);
		try {
			return Class.forName(className).getDeclaredMethod(methodName, argTypes);
		} catch (Throwable t) {
			t.printStackTrace();
			return null;
		}
	}

	private static void requireClassName(String className) {
		if (className == null || className.equals("")) {
			throw new IllegalArgumentException("className 不能为空");
		}
	}

	private static void requireMethodName(String methodName) {
		if (methodName == null || methodName.equals("")) {
			throw new IllegalArgumentException("methodName不能为空");
		}
	}

	private static void requireFieldName(String fieldName) {
		if (fieldName == null || fieldName.equals("")) {
			throw new IllegalArgumentException("fieldName 不能为空");
		}
	}
}
