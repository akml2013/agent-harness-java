# Excel数据报表技能

本技能是Excel文档生成的技能书，供你根据场景灵活运用。

## 一、MCP工具调用规范（重要！）

### 工具名称

Excel文档操作统一使用 `excel` 工具，不再有 `generate_excel`、`modify_excel`、`read_excel` 等独立工具。

### 调用模式

每个ReAct轮次只调用一次 `excel` 工具，多个文档操作通过 `actions` 数组打包在一次调用中完成。

#### 创建新文档

```json
{
  "action": "excel",
  "action_input": {
    "actions": [
      {"action": "create", "file_name": "xxx.xlsx"},
      {"action": "write_rows", "data": [["表头1", "表头2"], ["数据1", "数据2"]]},
      {"action": "set_column_width", "widths": [15, 20]}
    ]
  }
}
```

**关键规则**：`create` 必须是 `actions` 数组中的第一个操作！

#### 修改已有文档

```json
{
  "action": "excel",
  "action_input": {
    "file_key": "users/1/sessions/xxx/abc.xlsx",
    "actions": [
      {"action": "write_rows", "data": [["新增数据1", "新增数据2"]]},
      {"action": "set_cell_style", "row": 1, "col": 1, "bold": true}
    ]
  }
}
```

**关键规则**：`file_key` 是MinIO存储路径，由 `create` 操作返回，不是文件名！修改已有文档时必须传入 `file_key`。

#### 读取文档

```json
{
  "action": "excel",
  "action_input": {
    "file_key": "users/1/sessions/xxx/abc.xlsx",
    "action": "read"
  }
}
```

#### 单操作简写（无需数组）

当只需执行一个操作时，可以直接用 `action` 字段代替 `actions` 数组：

```json
{
  "action": "excel",
  "action_input": {
    "file_key": "users/1/sessions/xxx/abc.xlsx",
    "action": "set_cell_style",
    "row": 1,
    "col": 1,
    "bold": true
  }
}
```

### file_key 说明

- `file_key` 是文件在MinIO中的存储路径，格式如 `users/1/sessions/xxx/abc.xlsx`
- `create` 操作成功后会返回 `file_key`，后续操作必须使用此值
- **绝对不能**用文件名（如 `abc.xlsx`）代替 `file_key`
- 修改已有文档时，`file_key` 从前端传入或从 `read` 操作获取

## 二、Excel文档类型识别

| 类型 | 特征 | 典型场景 |
|------|------|----------|
| 数据报表 | 表头+数据行+汇总行 | 销售报表、库存报表、项目进度 |
| 财务报表 | 金额+千分位+百分比+合计行 | 利润表、资产负债表、成本分析 |
| 统计分析 | 数据+公式+条件格式 | 成绩统计、调查分析、质量报告 |
| 信息清单 | 多列信息+筛选+冻结 | 人员清单、设备台账、客户列表 |
| 对比分析 | 多组数据+图表 | 竞品对比、月度对比、预算vs实际 |

## 三、排版规范

### 表头样式
- 背景色：深蓝(#4472C4)或深灰(#404040)
- 字体颜色：白色(#FFFFFF)
- 字体：加粗，11-12pt
- 对齐：水平居中，垂直居中

### 数据区域样式
- 字体：10-11pt
- 文本：左对齐
- 数字：右对齐
- 百分比：0.00%格式
- 金额：#,##0.00格式
- 日期：yyyy-mm-dd格式

### 列宽参考

| 内容类型 | 建议宽度(字符数) |
|---------|----------------|
| 序号 | 6-8 |
| 中文人名 | 12-15 |
| 数字/编码 | 10-15 |
| 长文本/地址 | 25-35 |
| 日期 | 12-15 |
| 金额 | 15-18 |
| 百分比 | 10-12 |

### 常用数字格式

| 场景 | number_format | 示例 |
|------|--------------|------|
| 金额 | #,##0.00 | 1,250,000.00 |
| 整数千分位 | #,##0 | 1,250,000 |
| 百分比 | 0.00% | 15.00% |
| 日期 | yyyy-mm-dd | 2026-05-07 |
| 编号 | 0000 | 0001 |

## 四、分阶段策略

### 阶段1：创建文档与写入数据

使用excel工具的actions数组，将create和write_rows打包在一次调用中完成：

```json
{
  "action": "excel",
  "action_input": {
    "actions": [
      {"action": "create", "file_name": "销售报表.xlsx"},
      {"action": "write_rows", "data": [
        [{"value": "产品名称", "bold": true, "bg_color": "#4472C4", "font_color": "#FFFFFF"}, {"value": "销售额", "bold": true, "bg_color": "#4472C4", "font_color": "#FFFFFF"}],
        ["产品A", 125000],
        ["产品B", 89000]
      ]}
    ]
  }
}
```

表头使用对象格式带样式，数据行使用简单格式。

### 阶段2：样式与格式

使用actions数组将多个样式操作打包：

```json
{
  "action": "excel",
  "action_input": {
    "file_key": "users/1/sessions/xxx/销售报表.xlsx",
    "actions": [
      {"action": "set_range_style", "range": "A2:B10", "alignment": "right"},
      {"action": "set_column_width", "widths": [15, 18]},
      {"action": "set_cell_style", "row": 10, "col": 2, "bold": true},
      {"action": "merge_cells", "range": "A1:B1"},
      {"action": "set_formula", "row": 10, "col": 2, "formula": "SUM(B2:B9)"}
    ]
  }
}
```

### 阶段3：高级功能（按需）

```json
{
  "action": "excel",
  "action_input": {
    "file_key": "users/1/sessions/xxx/销售报表.xlsx",
    "actions": [
      {"action": "freeze_panes", "row": 2},
      {"action": "set_auto_filter", "range": "A1:B10"},
      {"action": "set_data_validation", "range": "C2:C10", "type": "list", "formula1": "\"未开始,进行中,已完成\""},
      {"action": "set_conditional_format", "range": "B2:B10", "type": "cell_is", "operator": "greaterThan", "formula": "100000", "bg_color": "92D050"},
      {"action": "set_sheet_print_settings", "orientation": "landscape", "fit_to_width": 1}
    ]
  }
}
```

### 阶段4：检查验证

使用excel工具的read操作检查文档内容和样式：

```json
{
  "action": "excel",
  "action_input": {
    "file_key": "users/1/sessions/xxx/销售报表.xlsx",
    "action": "read"
  }
}
```

确认数据完整性、格式正确性、公式计算正确、合并区域正确。

## 五、常见Excel模板

### 销售数据报表

```json
{
  "action": "excel",
  "action_input": {
    "actions": [
      {"action": "create", "file_name": "销售数据报表.xlsx"},
      {"action": "merge_cells", "range": "A1:D1"},
      {"action": "write_rows", "data": [
        ["2026年5月销售数据报表"],
        [{"value": "序号", "bold": true, "bg_color": "#4472C4", "font_color": "#FFFFFF"}, {"value": "产品", "bold": true, "bg_color": "#4472C4", "font_color": "#FFFFFF"}, {"value": "销售额", "bold": true, "bg_color": "#4472C4", "font_color": "#FFFFFF"}, {"value": "占比", "bold": true, "bg_color": "#4472C4", "font_color": "#FFFFFF"}],
        [1, "产品A", 125000, 0.35],
        [2, "产品B", 89000, 0.25]
      ]},
      {"action": "set_formula", "row": 5, "col": 3, "formula": "SUM(C3:C4)"},
      {"action": "set_cell_style", "row": 5, "col": 3, "bold": true},
      {"action": "set_range_style", "range": "D3:D4", "number_format": "0.00%"},
      {"action": "set_range_style", "range": "C3:C5", "number_format": "#,##0.00"},
      {"action": "set_column_width", "widths": [8, 15, 18, 12]},
      {"action": "freeze_panes", "row": 3},
      {"action": "set_auto_filter", "range": "A2:D5"}
    ]
  }
}
```

- 第1行：合并标题（跨所有列居中）
- 第2行：表头（蓝色背景白色字体加粗）
- 第3行起：数据行
- 最后一行：合计行（加粗，SUM公式）
- 冻结前2行，添加自动筛选

### 财务报表

- 金额列使用#,##0.00格式
- 百分比列使用0.00%格式
- 合计行使用SUM公式
- 关键数据使用条件格式高亮

### 项目进度表

- 状态列使用数据验证（下拉列表：未开始/进行中/已完成）
- 完成率使用百分比格式
- 已完成行使用条件格式（绿色背景）

### 人员信息清单

- 冻结首行表头
- 添加自动筛选
- 日期列使用yyyy-mm-dd格式
- 手机号列使用文本格式避免科学计数法

## 六、问题解决方案

### 不设列宽导致列挤在一起
生成后必须使用set_column_width设置列宽，根据内容类型选择合适宽度。

### 百分比显示为0.15
写入0.15 + 设置number_format:"0.00%"。

### 金额无千分位
设置number_format:"#,##0.00"。

### 长数字显示为科学计数法
设置number_format:"0"或"#,##0"，或在值前加制表符强制为文本。

### 公式不计算
set_formula设置公式后，Excel打开时会自动计算。确保公式语法正确。

### 合并单元格后内容丢失
合并前先在左上角单元格写入值，再执行merge_cells。

## 七、质量检查清单

- [ ] 表头样式是否正确（背景色、字体颜色、加粗）
- [ ] 列宽是否合适（内容不被截断、不浪费空间）
- [ ] 数字格式是否正确（金额千分位、百分比、日期）
- [ ] 对齐方式是否正确（文本左对齐、数字右对齐、表头居中）
- [ ] 合并区域是否正确（标题行、跨列表头）
- [ ] 公式是否正确（SUM/AVERAGE/COUNT等）
- [ ] 冻结窗格是否合理（表头行冻结）
- [ ] 数据是否完整（行数、列数正确）
