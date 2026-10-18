package dev.emi.emi.jemi.impl;

import mezz.jei.api.gui.widgets.IScrollGridWidgetFactory;
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory;
import net.minecraft.client.gui.ScreenRect;

/**
 * 从 JEI 的滚动格控件工厂里读出它打算把格子摆在哪。
 *
 * <p>JEI 的 {@code IScrollGridWidgetFactory} 只公开了 {@code getArea()}:
 * 区域宽度 = 列数 × 18 + 滚动条宽度,高度 = 可见行数 × 18。列数本身是 JEI 实现里的私有字段,
 * 所以先反射读 {@code columns},读不到时再按区域宽度反推。
 */
// JEI 15.20 起 ISlottedWidgetFactory 一族标记为 forRemoval,上游 EMI 对 JEI 废弃 API
// 已有大量同类 [removal] 警告,这里按例抑制
@SuppressWarnings("removal")
public record JemiScrollGridLayout(int x, int y, int columns, int rows) {
	/**
	 * JEI 槽位贴图是 18×18(16×16 物品 + 边框),物品坐标比格子原点大 1。
	 */
	public static final int SLOT_SIZE = 18;

	/**
	 * 物品相对格点(槽框左上角)的偏移。对齐 JEI 自绘槽框的实测基线为 0
	 * (x 轴另有 +1,见 JemiRecipeLayoutBuilder#addSlotToWidget);
	 * 需要整体微调槽位(比如贴图与这个约定差一像素)时改这一处即可。
	 */
	public static final int SLOT_ITEM_OFFSET = 0;

	/**
	 * JEI 的 {@code AbstractScrollWidget.getScrollBoxScrollbarExtraWidth()}:
	 * SCROLLBAR_WIDTH(14) + SCROLLBAR_PADDING(2)。
	 */
	private static final int SCROLLBAR_EXTRA_WIDTH = 16;

	private static final String COLUMNS_FIELD = "columns";

	/**
	 * @return 布局信息;如果不是滚动格控件、或者拿不到有效区域,返回 {@code null}
	 *         (调用方保持 JEMI 原本的行为)
	 */
	public static JemiScrollGridLayout of(ISlottedWidgetFactory<?> widgetFactory) {
		if (!(widgetFactory instanceof IScrollGridWidgetFactory<?> scrollGridFactory)) {
			return null;
		}
		final ScreenRect area;
		try {
			area = scrollGridFactory.getArea();
		} catch (Throwable t) {
			return null;
		}
		if (area == null || area.width() < SLOT_SIZE || area.height() < SLOT_SIZE) {
			return null;
		}
		var rows = Math.max(1, area.height() / SLOT_SIZE);
		var columns = readIntField(widgetFactory, COLUMNS_FIELD);
		if (columns <= 0) {
			// 反射读不到列数时按区域宽度反推(宽 = 列数×18 + 滚动条 16)
			columns = Math.max(1, (area.width() - SCROLLBAR_EXTRA_WIDTH) / SLOT_SIZE);
		}
		return new JemiScrollGridLayout(area.getLeft(), area.getTop(), columns, rows);
	}

	/**
	 * 沿父类链反射读 int 字段(JEI 的实现类里 {@code columns} 是私有字段,没有公开访问器)。
	 * 字段不存在继续往父类找,其他异常一律返回 -1(调用方按宽度反推兜底)。
	 */
	private static int readIntField(Object owner, String fieldName) {
		for (var type = owner.getClass(); type != null; type = type.getSuperclass()) {
			try {
				var field = type.getDeclaredField(fieldName);
				field.setAccessible(true);
				return field.getInt(owner);
			} catch (NoSuchFieldException ignored) {
				// 继续往父类找
			} catch (Throwable t) {
				return -1;
			}
		}
		return -1;
	}
}