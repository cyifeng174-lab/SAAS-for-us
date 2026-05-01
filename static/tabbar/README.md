# TabBar 图标说明

## 图标要求

项目需要 8 个 TabBar 图标文件，分为 4 组，每组 2 个（正常状态和选中状态）。

## 图标规格

- **尺寸**：81px × 81px（推荐）
- **格式**：PNG 格式
- **背景**：透明背景
- **颜色**：
  - 正常状态：灰色 (#7A7E83)
  - 选中状态：蓝色 (#4A90E2)

## 所需图标列表

### 1. 首页图标
- **文件名**：
  - `home.png` - 正常状态
  - `home-active.png` - 选中状态
- **图标样式**：房子图标
- **存放路径**：`static/tabbar/`

### 2. 开单图标
- **文件名**：
  - `billing.png` - 正常状态
  - `billing-active.png` - 选中状态
- **图标样式**：订单/收银台图标
- **存放路径**：`static/tabbar/`

### 3. 库存图标
- **文件名**：
  - `inventory.png` - 正常状态
  - `inventory-active.png` - 选中状态
- **图标样式**：仓库/箱子图标
- **存放路径**：`static/tabbar/`

### 4. 我的图标
- **文件名**：
  - `my.png` - 正常状态
  - `my-active.png` - 选中状态
- **图标样式**：用户/人像图标
- **存放路径**：`static/tabbar/`

## 图标获取方式

### 方式一：使用 iconfont（推荐）
1. 访问 [阿里巴巴矢量图标库](https://www.iconfont.cn/)
2. 搜索需要的图标（如"首页"、"订单"、"仓库"、"用户"）
3. 选择喜欢的图标，添加到购物车
4. 下载 PNG 格式，尺寸选择 81px
5. 使用图片编辑工具调整颜色

### 方式二：使用设计工具
1. 使用 Figma、Sketch 或 Adobe XD
2. 绘制简单的图标
3. 导出为 PNG 格式

### 方式三：使用在线图标生成器
1. 访问 [Flaticon](https://www.flaticon.com/)
2. 搜索并下载图标
3. 调整尺寸和颜色

## 图标设计建议

1. **简洁明了**：图标应该简单易懂，避免过于复杂
2. **风格统一**：所有图标应保持一致的设计风格
3. **辨识度高**：在小尺寸下也能清晰识别
4. **适配主题**：与应用整体风格协调

## 临时解决方案

如果暂时没有图标文件，可以：

1. **使用文字代替**：临时修改 `pages.json`，移除 `iconPath` 和 `selectedIconPath`，只保留 `text`
2. **使用占位图**：创建简单的纯色方块作为临时图标
3. **使用 emoji**：在页面标题中使用 emoji 作为临时标识

## 图标文件检查清单

- [ ] `static/tabbar/home.png`
- [ ] `static/tabbar/home-active.png`
- [ ] `static/tabbar/billing.png`
- [ ] `static/tabbar/billing-active.png`
- [ ] `static/tabbar/inventory.png`
- [ ] `static/tabbar/inventory-active.png`
- [ ] `static/tabbar/my.png`
- [ ] `static/tabbar/my-active.png`

## 注意事项

1. 图标文件必须放在 `static/tabbar/` 目录下
2. 文件名必须与 `pages.json` 中的配置一致
3. 建议使用 2 倍或 3 倍图以适配高清屏幕
4. 图标大小建议控制在 40KB 以内，避免影响加载速度
