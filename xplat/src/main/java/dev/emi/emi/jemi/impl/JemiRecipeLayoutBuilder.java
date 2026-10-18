package dev.emi.emi.jemi.impl;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.Lists;
import dev.emi.emi.runtime.EmiLog;

import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.ISlottedWidgetFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;

public class JemiRecipeLayoutBuilder implements IRecipeLayoutBuilder {
	public final List<JemiIngredientAcceptor> ingredients = Lists.newArrayList();
	public final List<JemiRecipeSlotBuilder> slots = Lists.newArrayList();
	public boolean shapeless = false;
	/**
	 * 每个控件工厂已经分出去多少个格子。用 {@link IdentityHashMap} 是因为同一个类别通常只持有
	 * 一个工厂实例,而不同工厂(一个配方里可能有多个滚动格)要各算各的。
	 */
	private final Map<ISlottedWidgetFactory<?>, Integer> assignedCounts = new IdentityHashMap<>();
	/** 只打一次滚动格布局日志的开关 */
	private static final AtomicBoolean SCROLL_GRID_LAYOUT_LOGGED = new AtomicBoolean(false);

	@Override
	public IRecipeSlotBuilder addSlot(RecipeIngredientRole role) {
		return addSlot(role, 0, 0);
	}

	@Override
	public IRecipeSlotBuilder addSlot(RecipeIngredientRole recipeIngredientRole, int x, int y) {
		JemiRecipeSlotBuilder builder = new JemiRecipeSlotBuilder(recipeIngredientRole, x, y);
		ingredients.add(builder.acceptor);
		slots.add(builder);
		return builder;
	}

	@Override
	public IIngredientAcceptor<?> addInvisibleIngredients(RecipeIngredientRole recipeIngredientRole) {
		JemiIngredientAcceptor acceptor = new JemiIngredientAcceptor(recipeIngredientRole);
		ingredients.add(acceptor);
		return acceptor;
	}

	@Override
	public void moveRecipeTransferButton(int posX, int posY) {
	}

	@Override
	public void setShapeless() {
		shapeless = true;
	}

	@Override
	public void setShapeless(int posX, int posY) {
		shapeless = true;
	}

	@Override
	public void createFocusLink(IIngredientAcceptor<?>... slots) {
	}

	@Override
	@SuppressWarnings("removal") // JEI 15.20 起 ISlottedWidgetFactory 标记为 forRemoval,JEMI 的这条实现路径必须继续对旧 API 生效
	public IRecipeSlotBuilder addSlotToWidget(RecipeIngredientRole role, ISlottedWidgetFactory<?> widgetFactory) {
		JemiScrollGridLayout layout = JemiScrollGridLayout.of(widgetFactory);
		if (layout == null) {
			// 不是滚动格控件,或者拿不到它的区域:保持原本的 (0, 0) 行为
			return addSlot(role, 0, 0);
		}
		// 按工厂实例记序号(每个滚动格工厂各自从 0 开始排)
		int index = assignedCounts.merge(widgetFactory, 1, Integer::sum) - 1;
		// 只在第一次布局滚动格时打一条日志,方便实机确认修复生效
		if (SCROLL_GRID_LAYOUT_LOGGED.compareAndSet(false, true)) {
			EmiLog.info("Widget slots from a JEI scroll grid are now laid out in a fixed grid on EMI: origin (" + layout.x() + ", " + layout.y() + "), " + layout.columns() + " columns, " + layout.rows() + " visible rows");
		}
		// 滚动格在 JEI 里由控件自己画槽框,JEMI 没有对应控件,所以给槽位补上标准槽框。
		// 超出一页能容纳的格子时顺序往下排,EMI 配方界面没有滚动条,至少保证物品不丢失、不重叠。
		// x 轴的 +1 是对齐 JEI 自绘槽框的实测基线(物品画在格点 +1)。
		int x = layout.x() + (index % layout.columns()) * JemiScrollGridLayout.SLOT_SIZE + JemiScrollGridLayout.SLOT_ITEM_OFFSET + 1;
		int y = layout.y() + (index / layout.columns()) * JemiScrollGridLayout.SLOT_SIZE + JemiScrollGridLayout.SLOT_ITEM_OFFSET;
		IRecipeSlotBuilder slot = addSlot(role, x, y);
		// 用 setBackground(…) 而不是 JEI 15.20 才有的 setStandardSlotBackground():
		// 兼容更早的 JEI,且 mod 之后自己设背景时会直接覆盖掉这个,不会两层叠画;
		// 槽框贴图来自 JEI 内部(反射获取),拿不到时只摆坐标、不画框。
		IDrawable slotBackground = JemiSlotTextures.getSlotBackground();
		if (slotBackground != null) {
			slot.setBackground(slotBackground, -1, -1);
		}
		return slot;
	}
}
