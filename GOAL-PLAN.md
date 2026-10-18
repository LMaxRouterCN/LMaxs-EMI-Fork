# GOAL-PLAN: EMI 1.20.1 fork 适配 JEI 15.62 运行时

## 任务
修复 LMax-1.20 分支上的 AbstractMethodError(用户在玩 MC,离场,授权自行推进)。

## 根因(已坐实)
- 运行时环境: JEI 1.20.1-forge 15.62.0.219(用户游戏内)
- EMI 编译依赖: jei_version=jei-1.20.1-fabric:15.20.0.102(gradle.properties)
- JEI 15.20 -> 15.62 期间,IRecipeSlotView 接口新增/收紧了 abstract 方法 getAllIngredientsList()
- EMI 的 JemiRecipeSlot(xplat/src/main/java/dev/emi/emi/jemi/impl/JemiRecipeSlot.java)
  implements IRecipeSlotView,编译自旧接口,缺该方法
- 触发面 x2(同一根因): 
  ① RecipeFillButtonWidget 构造 -> JemiRecipeHandler.canCraft -> jeiCraft
  ② tooltip 渲染 -> JemiRecipeHandler.render -> jeiCraft
  两处最终都进入 JEI RecipeTransferUtil.calculateRequiredCountsByUid:363

## 关键区分
- issue #949(IC2 hover 渲染 glitch+crash)是另一个 bug,已修复于 commit cedb10f4,在 LMax-1.20 内,本任务不碰
- 用户确认: EMI 上游最新版(1.1.25 系)也没修此 bug,纯自维护

## 修复策略: 方案 A(编译期全量暴露,拒绝运行时打地鼠)
1. jei_version bump 到 15.62.x 对应 fabric 版本(从 maven metadata 确认确切号)
2. gradlew 编译,让编译器列出全部接口缺口(不止 getAllIngredientsList 一个)
3. 逐个补实现(语义从 JEI 接口源码对齐),全部带注释
4. build 通过 = 二进制兼容闭环,出 jar 交用户实机验证

## 备用方案(若方案 A 缺口爆炸,几十个编译错误)
- B: 退一步只补 getAllIngredientsList 等最小集合,保持 15.20 编译 —— 地鼠模式,仅当 A 工程量失控时向用户报备后启用

## 环境备忘
- 工作目录 D:\Documents\mcmod\emi-Fork,分支 LMax-1.20(tip 4df0c567)
- 终端 pwsh 7.7.0-preview.3;exec 一律代码块格式(教训见长期记忆 003)
- read 用行号模式(剪贴板模式会丢换行)
- JEI maven: maven.blamejared.com, group mezz.jei
- JEI 源码: github.com/mezz/JustEnoughItems, 1.20.1 分支(LTS)
- 验收标准: gradlew build 零错误;用户实机 JEI 15.62.0.219 下填充按钮/tooltip 不再报错
## 状态更新 2026-10-06(编译首探)
- jei_version 已 bump 到 15.62.0.219(gradle.properties,已告知用户)
- 编译在 configure 阶段失败,死因不是接口缺口,是工具链:
  Architectury Loom 1.7.435 拒绝处理 Loom 1.17.20 构建的 JEI 15.62 工件
  -> 上游 EMI 依赖锁死在 15.20 的结构性原因浮出:工具链锁死
- 决策树(待接口对账数据):
  路径1 升级 Loom(>=1.17.20,可能连带 Gradle/architectury-plugin)= 根源方案,动 build 基础设施,等用户拍板
  路径2 保持工具链,接口对账后给实现类补"超前实现"(普通 public 方法,不加 @Override,
  JVM 运行时按方法签名命中新接口)—— 缺口少走路径2 今天出 jar,多则等用户拍板路径1
- JEI 15.62 fabric sources jar 从 maven.blamejared.com 下载解压中
  (raw.githubusercontent.com 被用户的 Steam++ 加速劫持超时;maven 直连正常,构建时 JEI 工件已成功下载)
## 状态更新 2026-10-06(接口对账+决策)
- IRecipeSlotView 15.62 源码在手(经 maven sources jar,绕开 GitHub raw):
  EMI 实现类缺 abstract 方法恰好 3 个,全部简单:
  1) getAllIngredientsList() -> List<@Nullable ITypedIngredient<?>> (15.34.0,@Unmodifiable,blank=null)
  2) getDisplayedIngredients() -> Stream<ITypedIngredient<?>> (15.52.0,EMI 无轮换限制=委托 getAllIngredients)
  3) getTagKey() -> Optional<TagKey<?>> (15.53.0,EMI 无此概念=恒 empty;实现体不引用 MC 类,擦除签名 ()Ljava/util/Optional;)
- 方案 A(编译期暴露)被工具链墙堵死:loom 1.7 在 configure 阶段拒收 15.62 工件,
  编译器没机会枚举缺口 -> 降级为源码级 diff 对账(15.20 vs 15.62 API 全树 diff = 缺口等价清单,
  接口只增不删,权威性等价于编译器输出),一 pass 补完,不是打地鼠
- jei_version 已回退 15.20.0.102(路径 2 编译对 15.20;bump 在 loom 1.7 下只会让 configure 挂)
- 路径 1(升 Loom 全家桶)保留为"根源方案",等 max 拍板,本轮不动 build 基础设施
- 待办:全树 diff -> 汇总所有接口新增 abstract 方法 -> 对照 jemi 包 19 个实现类
  -> 一 pass 实现(JemiRecipeSlot 三方法为主,兄弟类按 diff 结果定) -> 全量 build -> 出 jar
## 状态更新 2026-10-06(审计升级 + 首 patch)
- 修正:getTagKey 不能委托 EMI(EmiIngredient 只有 of(TagKey) 工厂,无共同标签查询语义)
  -> 按 JEI javadoc 返回 Optional.empty()(文档定义的合法语义空值)
- 审计升级:PowerShell 正则分类器无法区分 真新增abstract/重声明/签名变更/体碎片
  -> 换 Python 全量闭包审计 .agent_temp_files/audit_jei_api.py
  (JEI 15.62 API 继承闭包 abstract 集 vs EMI 实现类含父类链已声明方法集,MISSING/CHECK/OK)
- 已 patch xplat/.../jemi/impl/JemiRecipeSlot.java:
  + import net.minecraft.registry.tag.TagKey
  + 新增 3 个普通 public 方法(不加 @Override,编译对 15.20;注释含升级 loom 后补 @Override 提醒):
    getAllIngredientsList(空位保留 null,.toList 不可变)/getDisplayedIngredients(委托)/getTagKey(empty)
- 下一步:审计脚本判决 -> 按 MISSING 补其余实现类(builder/extras/tooltip/drawable/recipesGui)
  -> :xplat:compileJava -> 全量 build -> 出 jar -> commit -> max 实机验收
## 状态更新 2026-10-06(补丁第二轮)
- 已 patch(本轮 4 文件 11 方法,全部无 @Override + 注释):
  JemiRecipeSlotDrawable +4(3 委托 widget.slot + drawTooltip no-op)
  JemiPlaceable +1(JemiScrollGridWidget +1 / JemiRecipeSlotBuilder +1 同款 setPosition/6 真实现:
    15.20 default -> 15.62 abstract,区域+对齐算法,HorizontalAlignment/VerticalAlignment)
  JemiRecipeSlotBuilder +1 setFluidRenderer/5(忽略 TilingDirection,EMI tank 渲染不走 JEI 平铺)
- 判为无缺口(15.20 继承链已有):SlotBuilder 的 addFluidStackx3/setPosition/2/getWidth/getHeight
  (15.20 builder 已 extends IIngredientAcceptor+IPlaceable);JemiRecipesGui.getParentScreen
  (上游 EMI 自己已写注释掉 @Override 的超前实现 —— 技法同源);bookmark/filter/overlay/scrollbox
- 待 dump 定案(D1-D7 回执):JemiTooltipBuilder +4(clear/clearIngredient/addKeyUsageComponent/
  getLines —— getLines 的 Either import);JemiTextWidget +4 setTooltipx4(class_5632 对应类);
  JemiRecipeExtrasBuilder +6(addDrawableWidget/addTooltipArea/addRecipeArrowWidget/
  addRecipePlusSignWidget/addAnimatedRecipeArrowWidget/addAnimatedRecipeFlameWidget,
  返回 IDrawableWidget 新接口 —— 承接方式看 JemiWidgetBuilder + IDrawableWidget)
- 修复版审计脚本同轮重跑(交叉验证;已知解析器对无修饰符方法有漏检,人工清单优先)
## 状态更新 2026-10-06(补丁第三轮 + JVM 模型修正)
- 【模型修正】JVM 接口分发匹配 名字+完整擦除描述符(含返回类型):
  15.62 接口协变重声明(ITextWidget 的 setPosition/setTooltip 返回 ITextWidget)与
  raw IPlaceable 的 (...)IPlaceable 是不同 itable 槽位;解法=协变重声明+委托 super,
  编译器自动生成桥方法;返回类型为 15.56+ 新类型的方法源码级不可实现(硬边界)
- 已 patch 本轮:JemiTooltipBuilder +4(addKeyUsageComponent no-op / clear 真清空双仓 /
  clearIngredient 对称 no-op / getLines 返回 Either.left 文本映射,数据行不可还原已注释)
  + import Either/IJeiKeyMapping;三个 setPosition/6 补 @Override+桥方法注释
- 残余风险(源码级不可修,已记录):IRecipeExtrasBuilder 的 6 个 addXXXWidget 方法
  (返回 IDrawableWidget=15.56 新类型,描述符无法命中;仅影响主动用新 API 的 JEI 插件,
  max 的崩溃路径 RecipeTransferUtil=JEI 内部代码,已封死)
- 待定:setFluidRenderer/5 去/留取决于 TilingDirection 是否 15.20 已有(T1);
  JemiTextWidget +6(setPosition/2+6 协变重声明/setTooltipx4,数量取决于 T2)
- 本轮已跑:旧树存在性检查 T1-T6 + :xplat:compileJava(编译器=终极裁判)
## 状态更新 2026-10-06(补丁第四轮 + 收口)
- 编译首跑 4 错误全部归因:2 个=TooltipBuilder import 替换因文件空行布局未命中(已重锚定补上
  Either+IJeiKeyMapping);2 个=T1/T2 判决(见下)
- T 判决:TilingDirection 15.20 无 -> setFluidRenderer/5 从补丁降级为残余风险(方法+import 撤出,
  现场留注释);IRecipeWidgetTooltipCallback 15.20 无 -> ITextWidget 的回调重载进残余清单;
  IJeiKeyMapping 15.20 有(T3)只需 import;枚举常量 LEFT/CENTER/RIGHT、TOP/CENTER/BOTTOM 确认(T4)
- JemiTextWidget +5:setPosition/2 与 /6 协变重声明(@Override 合法,桥方法同时覆盖
  ITextWidget 擦除与 raw IPlaceable 擦除两槽位)+ setTooltip x3 no-op
  (参数类型坐实 class_5348=StringVisitable 非 Text,对照 JemiTooltipBuilder#add 坐实)
- 残余风险终版(同根因:签名引用 15.56+/15.62 新类型,15.20 编译面写不出,AME 仅当
  JEI 插件主动调用全新 API;max 崩溃路径 RecipeTransferUtil 不涉):
  1) IRecipeExtrasBuilder 6x addXXXWidget(返回 IDrawableWidget)
  2) IRecipeSlotBuilder.setFluidRenderer/5(参数 TilingDirection)
  3) ITextWidget.setTooltip(IRecipeWidgetTooltipCallback)
- 审计脚本退役(84 MISSING=default 误判+注解垃圾为主,只能当脚手架);闭包人工清账完毕
- 本轮:3 文件 patch + :xplat:compileJava -> 全量 build -> 产物清单
## 状态更新 2026-10-06(补丁第五轮)
- 编译第二次跑:31 警告(全部 [removal] 废弃 API=上游 EMI 在 JEI 15.20 下的常态,不碰)+
  1 错误:getAllIngredientsList 的泛型捕获(JemiUtil::getTyped 通配符返回被捕获为 CAP#1,
  List 不变性导致无法赋给 List<@Nullable ITypedIngredient<?>>)
- 修复:显式类型 witness .<@Nullable ITypedIngredient<?>>map(es -> ...orElse(null)),
  行为语义不变(空位 null 保序不可变列表)
- 本轮:1 处类型修复 -> :xplat:compileJava -> 全量 build -> 产物清单
## 任务完成 2026-10-06(JEI 15.62 兼容修复收口)
- :xplat:compileJava 绿(31 警告全为上游既有 [removal] 废弃 API;HandledScreenMixin renderBackground
  警告同为上游既有,均不碰)
- 全量 build 绿,fabric + forge 双平台产物就绪:
  fabric/build/libs/emi-1124.1-SNAPSHOT+1.20.1+fabric.jar(1.13MB,remapJar 运行时产物,验收用)
  forge/build/libs/emi-1124.1-SNAPSHOT+1.20.1+forge.jar(1.12MB)
  (dev-shadow 为开发用 named 映射版,不要装进游戏)
- 改面:gradle.properties(jei_version 净效果保持 15.20.0.102)+ xplat jemi 桥 7 文件 + 本文档
- max 验收清单(实例挂 JEI 15.62.0.219):
  1) 配方转移/填充按钮(原始崩溃点:JEI RecipeTransferUtil -> getAllIngredientsList)
  2) 配方界面槽位 tooltip(JemiTooltipBuilder 新方法路径)
  3) 配方浏览回归(确认无副作用)
  4) 若某 JEI 附属插件触发 AME:对照上文残余风险 3 族(该插件用了 15.56+ 全新 API)
- 残余风险 3 族与路径 1(升 loom 彻底消除)见上文,等 max 拍板
## 任务2 2026-10-18(JEMI addSlotToWidget 滚动格布局修复,1124.2)
- bug: JEI 插件用新式工厂 API(addSlotToWidget + IGuiHelper.createScrollGridFactory)
  产出的配方(如 CrockPot 猪灵以物易物)在 EMI 里所有物品叠在同一个格子 (0,0)
- 根因: JemiRecipeLayoutBuilder#addSlotToWidget 是 TODO 桩,直接 addSlot(role, 0, 0)
- 参照: NiButCrazy/EmiScrollGridFix(mixin 方案;本地 clone 于 .agent_temp_files/EmiScrollGridFix-ref)
  本 fork 的 JEMI 是 EMI 自己的代码,源码级修复,零 mixin
- 移植范围: 布局 + 槽框;参照的翻页系统/标签页隐藏不做(增强非 bug 本体)
- 修复设计:
  1) 新文件 jemi/impl/JemiScrollGridLayout.java: record(x,y,columns,rows) + of(factory)
     of() = instanceof IScrollGridWidgetFactory -> getArea()(catch Throwable 返 null;
     区域 null 或宽高小于 18 返 null);rows = max(1, h/18);
     columns = 反射沿父类链读私有字段 columns(读不到按 max(1,(w-16)/18) 反推,16=滚动条14+padding2)
  2) 新文件 jemi/impl/JemiSlotTextures.java: 反射取 JEI 标准槽位贴图
     Class.forName(mezz.jei.common.Internal).getTextures() -> 双名 getSlotDrawable/getSlot
     (老名优先),方法解析一次缓存且失败粘性,任何 Throwable 返 null(调用方只摆坐标不画框)
     注: 参照 mod 对全量 JEI jar 编译可直调 Internal;本 fork 对 JEI 只有 API 依赖
     (modCompileOnly,xplat 零 mezz.jei.common 引用已 grep 证实),必须整链反射
  3) JemiRecipeLayoutBuilder: 实例字段 assignedCounts(IdentityHashMap 按工厂实例记账;
     builder 每配方新建不复用,5 处 new 全是方法局部变量,已验证);
     重写 addSlotToWidget: layout 为 null 保持原行为;否则
     x = left + (index % columns)*18 + 1, y = top + (index / columns)*18(x 轴 +1 是参照实测基线);
     slot.setBackground(JemiSlotTextures.getSlotBackground(), -1, -1)(贴图拿不到则跳过)
     用 setBackground 而非 setStandardSlotBackground(): 兼容更早 JEI,mod 自设背景可直接覆盖不叠画
- 编译面验证(2026-10-18,javap 于 loom-cache 的 15.20.0.102 yarn-remapped jar):
  IScrollGridWidgetFactory@15.20 存在,getArea() 返回 net.minecraft.client.gui.ScreenRect;
  yarn 访问器名 getLeft()/getTop()/width()/height();
  IRecipeSlotBuilder.setBackground(IDrawable,int,int)@15.20 存在;
  两接口 15.20 起 @Deprecated(forRemoval) -> 方法级 SuppressWarnings("removal")
- 运行时面(用户 JEI 15.62.0.219): 15.62 的 ScrollGridWidgetFactory 与 15.20 同构
  (columns/visibleRows 私有 final 字段 + getArea 由 calculateSize 算),反射目标跨版本一致;
  接口调用经 loom 平台 remap 后二进制链接成立(fabric -> intermediary class_8030;
  forge -> mojmap navigation.ScreenRectangle;
  先例: EMI 自家 JemiScrollGridWidget.getScreenRectangle 在双平台工作)
- 发版: mod_version 1124.1 -> 1124.2;RELEASE=1 gradlew.bat build(fabric+forge 双 jar);
  commit(排除 .agent) -> push origin LMax-1.20 -> gh release create 1124.2+1.20.1
  (显式 --repo 指向本 fork 并 --target LMax-1.20,先 git remote -v 防打错仓库;
  notes 覆盖 1124.1 桥方法 + 1124.2 滚动格两项,1124.1 从未发过 release)
- 已知限制(写入 release notes): EMI 配方界面无滚动条,超出滚动格可见容量的槽位顺序向下延伸;
  大标签配方溢出部分不可见
