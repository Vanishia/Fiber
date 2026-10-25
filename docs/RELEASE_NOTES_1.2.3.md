# Fiber 1.2.3

## 中文

### 新增

- 编辑器底部新增图标工具栏，支持标题、加粗、斜体、列表、任务列表、代码块、引用及插入图片；长按图标可查看操作提示。
- 支持编辑撤销与重做，并支持外接键盘的常用撤销、重做快捷键。
- 支持为每个笔记库独立设置主页条目视图或瀑布流视图，切库时自动使用对应设置。
- 新增正文行距设置，支持 90%–130% 五档调节，与字体大小使用一致的胶囊控件；古诗预览可同时查看字号与行距效果。

### 优化与修复

- 优化热力图筛选切换，减少等待和短暂空白，计算完成后直接更新结果。
- 修复热力图按月份、年份筛选后，笔记数量、字数、记录时段及各库分布未随范围正确更新的问题。
- 改善 Markdown 表格换行、长内容布局及图片加载占位显示。
- 改进编辑与预览切换、图片插入和保存过程中的状态处理，避免过期的异步结果干扰当前笔记，保留保存期间继续输入的内容。
- 修复外部分享、文件导入与页面切换并发时可能出现的导航卡住、重复跳转及原编辑草稿丢失问题。
- 调整主页顶栏的滚动收起效果及瀑布流卡片样式。
- 精简设置页布局：正文行距位于字体大小下方，推荐配色收为“主题颜色”右侧的五个圆点，选色更直观。

## English

### New

- Added an icon toolbar to the editor for headings, bold, italic, bullet lists, task lists, code blocks, quotes, and images. Long-press an icon to see its label.
- Added undo and redo for editing, including common shortcuts on an external keyboard.
- Added per-library home view settings: choose a list or waterfall layout for each library, applied automatically when switching libraries.
- Added five text line spacing levels from 90% to 130%, using the same segmented control as font size. The poem preview now reflects both font size and line spacing.

### Improvements and Fixes

- Improved heatmap filter switching to reduce waiting and brief blank states. Results now update as soon as calculation finishes.
- Fixed note counts, character counts, time-of-day statistics, and library distributions not updating correctly for the selected month or year.
- Improved Markdown table wrapping, long-content layout, and image loading placeholders.
- Improved state handling when switching between editing and previewing, inserting images, and saving. Stale asynchronous results no longer interfere with the current note, and text entered during a save remains available.
- Fixed navigation getting stuck, duplicate navigation, and loss of an existing editor draft when external sharing or file import overlaps with a page transition.
- Refined the home toolbar's scroll-collapse behavior and waterfall card styling.
- Streamlined the settings layout: line spacing now sits below font size, and five recommended color swatches appear beside “Theme color” for quicker selection.
