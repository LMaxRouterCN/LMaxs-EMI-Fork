package dev.emi.emi.jemi.impl;

import java.lang.reflect.Method;

import mezz.jei.api.gui.drawable.IDrawable;

/**
 * 反射取 JEI 的标准槽位贴图(18×18),给 JEMI 布局出来的滚动格槽位补槽框。
 *
 * <p>来源是 JEI 的内部入口 {@code mezz.jei.common.Internal.getTextures()}:
 * JEI 15.20 自己的滚动格控件就是这么取的({@code ScrollGridRecipeWidget} ->
 * {@code Internal.getTextures().getSlot()})。
 *
 * <p>本工程对 JEI 只有 API 依赖(modCompileOnly,编译面看不到 {@code mezz.jei.common}),
 * 所以整条链路走反射;方法名两种都试:JEI 15.20 起是 {@code getSlot()},
 * 更早的版本是 {@code getSlotDrawable()}。拿不到(JEI 运行时还没就绪、版本对不上等)
 * 返回 {@code null},调用方只摆坐标、不画槽框。
 */
public final class JemiSlotTextures {
	/** JEI 内部入口类,不在 API 包里,只能反射 */
	private static final String INTERNAL_CLASS = "mezz.jei.common.Internal";
	private static final String GET_TEXTURES_METHOD = "getTextures";
	/** 槽位贴图的方法名,老名字优先 */
	private static final String[] SLOT_DRAWABLE_METHOD_NAMES = {"getSlotDrawable", "getSlot"};

	/** Internal.getTextures() 的 Method;解析成功后缓存,失败不缓存(JEI 可能晚于 EMI 就绪) */
	private static volatile Method getTexturesMethod;

	/** 贴图方法解析结果,解析一次后粘性(拿到贴图注册表后类就稳定了,失败也不必重试) */
	private static boolean slotMethodResolved;
	private static Method slotDrawableMethod;

	private JemiSlotTextures() {
	}

	/**
	 * @return JEI 标准槽位贴图;拿不到返回 {@code null}(调用方静默降级,只摆坐标不画框)
	 */
	public static IDrawable getSlotBackground() {
		try {
			var textures = getTextures();
			if (textures == null) {
				return null;
			}
			var method = resolveSlotDrawableMethod(textures.getClass());
			return method == null ? null : (IDrawable) method.invoke(textures);
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 反射调用 {@code Internal.getTextures()}。Method 解析成功后缓存(失败不缓存:
	 * JEI 的内部类可能晚于 EMI 的第一次配方构建才可用,暂时性失败下次重试);
	 * invoke 每次现取,JEI 未就绪时它会返回 null 或抛异常,这里都归为 null。
	 */
	private static Object getTextures() {
		var method = getTexturesMethod;
		if (method == null) {
			try {
				method = Class.forName(INTERNAL_CLASS).getMethod(GET_TEXTURES_METHOD);
			} catch (Throwable t) {
				return null;
			}
			getTexturesMethod = method;
		}
		try {
			return method.invoke(null);
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 在贴图注册表的实际类上解析槽位贴图方法,名字按版本双试,只解析一次(粘性)。
	 */
	private static synchronized Method resolveSlotDrawableMethod(Class<?> texturesClass) {
		if (!slotMethodResolved) {
			for (var name : SLOT_DRAWABLE_METHOD_NAMES) {
				try {
					slotDrawableMethod = texturesClass.getMethod(name);
					break;
				} catch (NoSuchMethodException ignored) {
					// 试下一个名字
				}
			}
			slotMethodResolved = true;
		}
		return slotDrawableMethod;
	}
}