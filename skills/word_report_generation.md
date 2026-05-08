# Word文档制作技能

本技能提供Word文档制作的领域知识和最佳实践，帮助你制作专业规范的Word文档。

## 一、MCP工具调用规范（重要！）

### 工具名称

Word文档操作统一使用 `word` 工具，不再有 `generate_word`、`modify_word`、`read_word` 等独立工具。

### 调用模式

每个ReAct轮次只调用一次 `word` 工具，多个文档操作通过 `actions` 数组打包在一次调用中完成。

#### 创建新文档

```json
{
  "action": "word",
  "action_input": {
    "actions": [
      { "action": "create", "file_name": "xxx.docx" },
      { "action": "add_paragraph", "text": "..." },
      { "action": "set_page_layout", "paper_size": "A4" }
    ]
  }
}
```

**关键规则**：`create` 必须是 `actions` 数组中的第一个操作！

#### 修改已有文档

```json
{
  "action": "word",
  "action_input": {
    "file_key": "users/1/sessions/xxx/abc.docx",
    "actions": [
      { "action": "add_paragraph", "text": "..." },
      { "action": "add_table", "rows": 3, "cols": 4 }
    ]
  }
}
```

**关键规则**：`file_key` 是MinIO存储路径，由 `create` 操作返回，不是文件名！修改已有文档时必须传入 `file_key`。

#### 读取文档

```json
{
  "action": "word",
  "action_input": {
    "file_key": "users/1/sessions/xxx/abc.docx",
    "action": "read"
  }
}
```

#### 单操作简写（无需数组）

当只需执行一个操作时，可以直接用 `action` 字段代替 `actions` 数组：

```json
{
  "action": "word",
  "action_input": {
    "file_key": "users/1/sessions/xxx/abc.docx",
    "action": "add_paragraph",
    "text": "..."
  }
}
```

### file_key 说明

- `file_key` 是文件在MinIO中的存储路径，格式如 `users/1/sessions/xxx/abc.docx`
- `create` 操作成功后会返回 `file_key`，后续操作必须使用此值
- **绝对不能**用文件名（如 `abc.docx`）代替 `file_key`
- 修改已有文档时，`file_key` 从前端传入或从 `read` 操作获取

## 二、文档类型识别与策略选择

### 需要封面的文档

技术方案、用户手册、项目报告、论文等正式文档，需要封面页。

**策略**：create创建文档 → add_paragraph添加封面内容 → insert_section_break分节 → add_paragraph添加正文 → set_header/set_footer设置页眉页脚

### 不需要封面的文档

周报、会议纪要、简报等日常文档，直接从标题开始。

**策略**：create创建文档 → set_page_layout设置页面 → add_paragraph添加标题和正文 → set_header/set_footer设置页眉页脚

### 模板场景

用户上传文件并要求"按照这个格式"、"基于模板"等。

**策略**：word工具read读取模板 → replace_text替换占位符 → set_cell_value修改表格 → 传入save_as另存为新文件

**关键**：模板场景必须传save_as参数！否则原模板文件会从前端消失！

## 三、排版规范

### 字体规范

- 标题：黑体(SimHei)
- 正文：宋体(SimSun)
- 英文/数字：Times New Roman
- 代码：Consolas
- **同一级别的标题必须使用完全相同的字体和字号**，不允许出现同为二级标题但一个加粗一个不加粗的情况

### 字号规范

- 一级标题：16pt加粗（三号），黑体，居中
- 二级标题：14pt加粗（四号），黑体，靠左
- 三级标题：12pt加粗（小四），黑体，靠左
- 正文：12pt（小四），宋体
- 页眉页脚：9pt
- 图题/表题：10.5pt（五号）
- **标题层级不能跳级**（如一级标题后不能直接跟三级标题）

### 段落规范

- 首行缩进：2字符（first_line_indent: 2）
- 行距：1.5倍（line_spacing: 1.5）
- 标题段前段后：0.5行
- 正文段前段后：0行
- 标题对齐：一级居中(CENTER)，其余靠左(LEFT)
- 正文对齐：两端对齐(BOTH)
- **同类型段落必须保持一致的缩进、行距和对齐方式**

### 页面规范

- A4纸（paper_size: A4）
- 页边距：上下2.54cm，左右3.17cm
- 页眉距边界1.5cm，页脚距边界1.75cm
- 纵向(orientation: portrait)

### 页眉页脚规范

- 页眉：居中，9pt，内容为文档标题或章节名
- 页脚：居中，**只能是纯数字页码**（如"1"、"2"），不能有"第X页"、"Page X"等文字
- 封面页不显示页眉页脚（通过分节实现）

### 表格规范

- 学术论文：三线表（顶线、表头底线、底线）
- 数据报表：全框线表
- **同一文档中表格风格必须统一**，不能混用三线表和全框线表
- 表头：蓝色背景(#4472C4)白色字体加粗
- 列宽：中文名12-15cm，数字10-15cm，长文本25-35cm
- 表格总宽度一般不超过14cm

### 图片规范

- 居中显示（alignment: center）
- 图题在图片下方，宋体10.5pt斜体
- 宽度不超过页面2/3（max_width: 450）
- 保持宽高比（keep_aspect_ratio: true）

## 四、分阶段制作策略

### 第一阶段：创建文档+页面设置

使用word工具的actions数组，将create和set_page_layout打包在一次调用中完成：

```json
{
  "action": "word",
  "action_input": {
    "actions": [
      { "action": "create", "file_name": "报告.docx" },
      {
        "action": "set_page_layout",
        "paper_size": "A4",
        "orientation": "portrait"
      }
    ]
  }
}
```

### 第二阶段：搭建骨架

添加各级标题，章节间用insert_page_break分页。需要封面的文档先添加封面内容，再用insert_section_break分节。将多个add_paragraph操作打包在actions数组中。

### 第三阶段：填充内容

在标题间插入正文段落、表格、图片。使用add_paragraph添加内容，通过style中的heading_level区分标题和正文。尽量将同一区域的多个操作打包。

### 第四阶段：设置页眉页脚

使用set_header设置页眉，set_footer设置页脚（含页码）。可与第三阶段操作合并到同一个actions数组中。

### 第五阶段：检查完善

使用word工具的read操作检查文档结构，发现问题再用word工具微调：

```json
{
  "action": "word",
  "action_input": {
    "file_key": "users/1/sessions/xxx/报告.docx",
    "action": "read"
  }
}
```

## 五、常见文档类型模板

### 周报/月报

```json
{
  "action": "word",
  "action_input": {
    "actions": [
      { "action": "create", "file_name": "工作周报.docx" },
      {
        "action": "add_paragraph",
        "text": "工作周报",
        "style": { "heading_level": 1 }
      },
      {
        "action": "add_paragraph",
        "text": "本周工作",
        "style": { "heading_level": 2 }
      },
      { "action": "add_paragraph", "text": "1. 完成了xxx任务..." },
      {
        "action": "add_table",
        "rows": 4,
        "cols": 3,
        "data": [
          ["任务", "状态", "备注"],
          ["任务1", "已完成", "-"],
          ["任务2", "进行中", "-"],
          ["任务3", "未开始", "-"]
        ]
      },
      { "action": "set_footer", "text": "", "page_number": true }
    ]
  }
}
```

### 技术方案

```json
{
  "action": "word",
  "action_input": {
    "actions": [
      { "action": "create", "file_name": "技术方案.docx" },
      {
        "action": "add_paragraph",
        "text": "技术方案",
        "style": { "heading_level": 1, "alignment": "CENTER" }
      },
      { "action": "add_paragraph", "text": "2026年5月" },
      { "action": "insert_section_break" },
      {
        "action": "add_paragraph",
        "text": "目录",
        "style": { "heading_level": 1 },
        "insert_toc": true
      },
      {
        "action": "add_paragraph",
        "text": "一、项目概述",
        "style": { "heading_level": 2 }
      },
      { "action": "add_paragraph", "text": "本项目旨在..." },
      { "action": "set_header", "text": "技术方案" },
      { "action": "set_footer", "text": "", "page_number": true }
    ]
  }
}
```

### 会议纪要

```json
{
  "action": "word",
  "action_input": {
    "actions": [
      { "action": "create", "file_name": "会议纪要.docx" },
      {
        "action": "add_paragraph",
        "text": "会议纪要",
        "style": { "heading_level": 1 }
      },
      {
        "action": "add_table",
        "rows": 4,
        "cols": 2,
        "data": [
          ["时间", "2026-05-08"],
          ["地点", "会议室A"],
          ["主持人", "张三"],
          ["参会人", "李四、王五"]
        ]
      },
      {
        "action": "add_paragraph",
        "text": "议题与决议",
        "style": { "heading_level": 2 }
      },
      { "action": "add_paragraph", "text": "1. 讨论了xxx事项..." },
      { "action": "set_footer", "text": "", "page_number": true }
    ]
  }
}
```

## 六、常见问题与解决方案

### 空白页

- 原因：表格太大、多余分页符、段后间距过大
- 解决：set_table_width缩小表格、delete_paragraph删除多余分页符

### 黑色方点

- 原因：段落意外继承了列表编号属性
- 解决：系统已自动清除，若仍出现，用set_paragraph_style重新设置段落样式

### 表格溢出

- 原因：列宽未设置或表格总宽超过页面
- 解决：set_column_width设置列宽、set_table_width设置总宽度

### 文本替换不生效

- 原因：old_text与文档实际文本不完全匹配
- 解决：先用word工具read操作确认精确文本；使用fuzzy_match: true模糊匹配

### 格式不一致

- 原因：样式未统一设置
- 解决：使用set_paragraph_style和set_text_style统一设置样式

## 七、质量检查清单

### 结构检查

1. 文档结构是否正确（标题层级、章节顺序）
2. 标题层级是否连续（不能跳级，如一级后直接跟三级）
3. 段落顺序是否正确（内容没有跑到错误的位置）
4. 无多余空白页

### 样式一致性检查（重要！）

5. **同级别标题样式是否完全一致**（同为二级标题必须都是14pt加粗黑体，不能有的加粗有的不加粗）
6. **正文段落样式是否一致**（字体、字号、缩进、行距必须统一）
7. **表格风格是否统一**（不能混用三线表和全框线表）
8. **页眉页脚格式是否统一**（所有节的页眉页脚样式一致）

### 页眉页脚检查

9. 页脚是否为纯数字页码（不能有"第X页"等文字）
10. 封面页是否不显示页眉页脚
11. 页码是否从正文开始编号（封面不计入页码）

### 内容完整性检查

12. 表格数据是否完整（无遗漏行列）
13. 图片是否插入成功且居中
14. 文档属性是否设置（标题、作者）

### 格式检查

15. 标题是否正确显示（无黑点、无异常分页）
16. 页面设置是否合理（页边距、纸张方向）
17. 无黑色方点
