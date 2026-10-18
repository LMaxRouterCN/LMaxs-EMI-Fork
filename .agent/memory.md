<!-- ID:001 -->
2026-10-05 max的构建环境事实:
1. PokerAgent exec每轮任务结束会杀掉gradle daemon(回执有提示"任务结束后仍有后台进程存活已终止"),所有gradle构建都是冷启动,耗时比正常IDE环境显著更长(xplat compileJava冷启动9m34s)。长构建建议直接跑完整task别指望daemon复用。
2. gradle wrapper下载services.gradle.org直连会PKIX失败(网络层TLS干扰,无代理env,用户级gradle无任何代理/镜像配置)。修复方法:从~/.gradle/wrapper/dists/下另一个hash目录复制完整dist到本仓库wrapper对应的hash目录,并新建空的"gradle-x.x-bin.zip.ok"文件,wrapper见到.ok即跳过下载。此法只动用户级缓存不动仓库。
3. gradle用户目录:C:\Users\LLL95\.gradle,JAVA_HOME=E:\Java\Zulu\zulu-17
tag: 环境, gradle, 构建, PokerAgent
<!-- END:001 -->
<!-- ID:002 -->
2026-10-05 max的EMI fork项目状态:
- fork: LMaxRouterCN/EMI-Fork(曾用名emi-Fork,旧URL靠GitHub重定向)
- 工作分支LMax-1.20,跟踪上游emilyploszaj/emi的1.20分支
- 版本命名习惯:mod_version=1124.1意为"基于上游1.1.24的个人版",上游1.1.22时是1122.1
- 个人改动维护点:JemiRecipeHandler.java的Math.min修复(上游issue#949,防subList越界),位于shaped配方inputs处理段
- 已知无关问题:JEI 15.20无getAllIngredientsList方法,AbstractMethodError与之无关
- merge upstream后push,fork内upstream→自己分支的PR会被GitHub自动识别为merged,无需手动关闭
tag: 项目, EMI, EMI-Fork, fork, LMax-1.20, 版本习惯
<!-- END:002 -->
<!-- ID:003 -->
PokerAgent exec环境教训(2026-10-06,EMI仓库实测):
1. 单行exec内多语句曾整段静默失败——回执"命令已执行,无输出",脚本一行没跑。执行后必须验证副作用(git log/status),不能信"无输出=无事"
2. 单行指令参数用反引号定界,参数内PowerShell反引号转义(`r`n)会与定界符冲突——换行符用[string][char]13或Add-Content等无反引号写法
3. memory/search是PokerAgent框架指令,直接放【cmd】标签内,不要套在exec里(会被PowerShell当cmdlet报错)
4. 【正解】exec官方推荐代码块格式:单行模式特殊字符会破坏内容、多行命令仅PowerShell可靠;单代码块=同一进程共享变量(cd可持续有效),多代码块=独立进程无状态;完成判定=顶层进程退出+1.5s排水窗。自2026-10-06起exec一律代码块格式
tag: PokerAgent, 环境, exec, 教训
<!-- END:003 -->
<!-- ID:004 -->
EMI fork工作流与背景(max自述,2026-10-06,源自早期会话原文):
- 分支分工(readme分支):LMax-1.20=max所有改动(适合+不适合上游都在),永不开PR,先在此开发;1.20-for-upstream-pr=只含适合上游的改动,从LMax-1.20复制过去开PR
- jar名"测试"后缀=max自己的分类标记(分离对游戏内容无影响的模组),无技术含义
- 环境:JEI 1.20.1-forge-15.62.0.219;EMI 1.20.1上游半死(bug不修,PR不合),max自维护
- 早期会话:已修#949(cedb10f4,JemiRecipeHandler.java,已含于LMax-1.20 merge 95e38dc8);AbstractMethodError(JemiRecipeSlot未实现JEI 15.62的IRecipeSlotView.getAllIngredientsList;触发:①填充按钮构造②tooltip渲染,均经jeiCraft→JEI RecipeTransferUtil)当时只诊断未修——现任务
tag: EMI, EMI-Fork, LMax-1.20, 工作流, JEI
<!-- END:004 -->
<!-- ID:005 -->
EMI fork工具链与JVM方法分发的关键事实(2026-10-06,实测修正):
1. 构建链=Architectury Loom 1.7.435+Gradle 8.8;Loom 1.7无法处理Loom 1.17.20构建的mod工件(JEI 15.62+),这是上游EMI把JEI锁死在15.20的结构性根因;升级Loom是根源方案但动build基础设施,需max拍板
2. max开着Steam++(watt toolkit)加速GitHub:raw.githubusercontent.com经其劫持会超时,maven.blamejared.com直连正常;JEI源码优先走maven sources jar:https://maven.blamejared.com/mezz/jei/jei-1.20.1-fabric/<版本>/jei-1.20.1-fabric-<版本>-sources.jar
3. 超前实现(编译旧接口+运行时新接口):普通public方法不加@Override(15.20接口没该方法);【修正】JVM接口分发匹配"方法名+完整擦除描述符,描述符含返回类型"——15.62接口以协变返回类型(如ITextWidget)重声明的方法,与raw接口(如IPlaceable)的(...)IPlaceable是两个itable槽位,基类T返回实现只喂饱后者;解法=在实现类以协变返回类型重声明并委托super,编译器见到与15.20可见父方法的擦除差异会自动生成桥方法,两描述符并存;返回/参数类型为15.56+才存在的类型(如IDrawableWidget/TilingDirection若无)时源码级不可实现=路径2硬边界
4. 审计脚本经验:正则解析Java接口对default关键字捕获不可靠(误把default判成abstract)、注解@Deprecated(since=...)会解析成假方法;结构化body检测(括号计数)可靠——"无body=必须实现的abstract"可信,"有body"标成abstract的一律是default误判
tag: EMI, EMI-Fork, gradle, 构建, 环境, JEI, JVM
<!-- END:005 -->
<!-- ID:006 -->
EMI fork 的 JEI 15.62 兼容修复已完成(2026-10-06, LMax-1.20 分支 commit 4cccd7ce): jemi 桥 7 类 18 方法超前实现, 编译对 JEI 15.20.0.102, 普通 public 无 @Override, 协变重声明自动生成桥方法(技法详见 memory 005), 编译与全量 build 双绿, fabric/forge 双平台产物就绪(验收用 build/libs 下非 dev-shadow 的 remapJar 产物)。残余风险 3 族: 签名引用 JEI 15.56 以上新类型(IDrawableWidget 的 6 个 addXXXWidget / setFluidRenderer 第5参 TilingDirection / ITextWidget.setTooltip 回调重载), 15.20 编译面源码级不可写, 仅当 JEI 插件主动调用全新 API 才触发 AME。彻底方案是升 Architectury Loom 到 1.17.20 以上, 等 max 拍板, 完整记录在仓库 GOAL-PLAN.md
tag: EMI, EMI-Fork, JEI, 项目
<!-- END:006 -->
<!-- ID:007 -->
2026-10-08: max在玩1.20.1 Forge大型整合包(含Create 6.0.8, EMI 1124.1-SNAPSHOT, emi_accelerator 1.0.1, acceleratedrendering, apotheosis, thermal, tconstruct, IE, farmersdelight, epicfight, tacz等)。已诊断出报错根因: emi_accelerator把EmiSearch.bake丢进ForkJoinPool后台线程,与acceleratedrendering注入Font的非线程安全fastutil宽度缓存并发冲突,AIOOBE但不崩溃,仅搜索索引残缺。推荐根治方案是删emi_accelerator。修复选项: 1.删emi_accelerator 2.删acceleratedrendering 3.报上游issue。
tag: MC整合包环境, 报错诊断, EMI
<!-- END:007 -->
<!-- ID:008 -->
gh CLI 在多 remote 仓库里解析默认仓库的优先顺序是 github > upstream > origin(为 fork 场景设计,裸命令默认指向上游)。emi-Fork 项目里 origin=LMaxRouterCN/EMI-Fork(fork 本体),upstream=emilyploszaj/emi,裸 gh release/issue 命令会打到 upstream——一切 gh 操作必须显式 --repo LMaxRouterCN/EMI-Fork。2026-10-18 亲证:gh release list 显示的是 upstream 的 releases。
tag: gh, fork, EMI-Fork, 工作流, 教训, 环境
<!-- END:008 -->
<!-- ID:009 -->
gh 多 remote 场景的第二层教训(2026-10-18,承接记忆008):不止"执行"要显式 --repo,"查证"也要——曾用裸 gh release list 看到 upstream 列表,错误推论"fork 上从未发过 1124.1"写进了 release notes("未单独发布,随本版分发"),实际 fork 的 1124.1 早已于 2026-10-07 发布(tag 就是裸的"1124.1",资产名带-SNAPSHOT因为那次没设 RELEASE=1)。规则:关于 fork 仓库状态的任何事实断言,必须来自显式 --repo 的查询输出,不能从裸命令的输出反推"不存在"。EMI-Fork 的 tag 命名由此前版本的不一致:1124.1(裸)vs 1124.2+1.20.1(带MC版本)。
tag: 教训, gh, fork, EMI-Fork, 工作流
<!-- END:009 -->
<!-- ID:010 -->
exec回执里gh输出的中文出现mojibake(典型形状如淇澶嶆)不代表GitHub远端数据坏:gh CLI内部一律UTF-8,是PowerShell回执管道按GBK误读的显示层问题。判定法(2026-10-18实测有效):用Invoke-RestMethod拉GitHub API原始body,NET层按charset自动解码,再以UTF-8无BOM落盘,最后用read指令读取,全链不经控制台代码页;实测view乱码但API读回与源文件逐字一致,远端干净。附:memory或remember的内容里写字面指令标识符(尤其中文方括号形式的代码块结束标签)会令解析器提前闭合代码块,产生幻影未知指令报错,2026-10-18两次实证;引用标识符一律用英文方括号或纯文字描述。
tag: 教训, gh, exec, PokerAgent, 工作流, EMI-Fork, 解析器
<!-- END:010 -->
<!-- ID:011 -->
max 的 fork 项目统一命名样式(2026-10-18 确立):显示名格式为 LMaxs + 空格 + 原项目名 + 空格 + Fork,注意 LMaxs 无撇号;编译产物 jar 同步改名(社区惯例全小写连字符);首个应用对象:EMI-Fork 改为 LMaxs EMI Fork。以后 max fork 的任何项目均沿用此模板。红线:mod id 永远保持原值不动,只改显示名/仓库名/文件名这三层皮。
tag: 用户偏好, 命名, fork, 版本习惯, 项目
<!-- END:011 -->
<!-- ID:012 -->
2026-10-18解析器事故定案:replace指令的code内容含双引号(场景:toml与json的displayName和name字段值本身带引号)会击穿块扫描,级联幻影报错:指令解析失败、缺少参数、内容行泄漏成未知指令、标签截断;当轮三处replace全部未执行(无已替换单据),文件零污染。实证矩阵:exec与remember内容含双引号均多次安全,唯replace致命。规则:凡内容含引号一律走exec的IO.File通道(ReadAllText加Replace加WriteAllText,UTF8无BOM编码器,gradle.properties带BOM会毒化首行key),replace只用于纯净文本。附:回执中指令解析失败声称其后所有内容均未执行不可信,解析器实际按幸存块继续处理,判断真实执行集只认成功单据。
tag: 解析器, PokerAgent, replace, exec, 教训
<!-- END:012 -->
