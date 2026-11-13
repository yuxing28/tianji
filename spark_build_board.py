import json, os

with open("b64data.json") as f:
    b64 = json.load(f)

# ← EDIT: 填入 Phase 3 推导的方案数据
schemes = [
    {
        "id": "a",
        "name": "A 墨",
        "desc": "近黑中性底加一线雾青。去掉所有装饰，靠留白、细线与秩序立住气质——高级感的黑，Nothing OS 式克制。",
        "keywords": ["克制", "高级黑", "细线", "留白"],
        "font": "HarmonyOS Sans SC",
        "img_keys": ["a1", "a2", "a3"],
        "img_labels": ["器物 · 近黑与雾青", "牌背 · 一线冷光", "墨晕 · 负空间"],
        "primary": ["#8FC7CC", "#C2E2E5", "#4E8A90", "#EDF6F7", "#22464A"],
        "secondary": ["#3A3D42", "#6B7077", "#232529", "#F4F5F6", "#0C0D0F"],
        "btn_bg": "#8FC7CC", "btn_color": "#0C0D0F",
        "input_border": "#4E8A90",
        "card_border": "#232529",
        "tag_bg": "#1E2126", "tag_color": "#C2E2E5",
        "toggle_bg": "#8FC7CC",
    },
    {
        "id": "b",
        "name": "B 宣",
        "desc": "暖纸白底、墨字、一线朱砂。编辑部式的排版气质，宋体标题把玄学拉回书卷感，安静耐看。",
        "keywords": ["编辑感", "书卷气", "宋体", "朱砂"],
        "font": "Noto Serif SC",
        "img_keys": ["b1", "b2", "b3"],
        "img_labels": ["纸页 · 朱砂一点", "文房 · 晨光", "静物 · 象牙与墨"],
        "primary": ["#C3402B", "#E08A7A", "#8F2A1C", "#FAEEE9", "#5C1A10"],
        "secondary": ["#57564F", "#9C9A90", "#2F2E2A", "#FAF9F6", "#191813"],
        "btn_bg": "#C3402B", "btn_color": "#FAF9F6",
        "input_border": "#C9C7BE",
        "card_border": "#E8E6DE",
        "tag_bg": "#FAEEE9", "tag_color": "#8F2A1C",
        "toggle_bg": "#C3402B",
    },
    {
        "id": "c",
        "name": "C 雾",
        "desc": "低饱和灰绿与暖灰，冥想 App 式的呼吸感。用户求的是心安，界面先让心静下来。",
        "keywords": ["呼吸感", "低饱和", "疗愈", "圆润"],
        "font": "HarmonyOS Sans SC",
        "img_keys": ["c1", "c2", "c3"],
        "img_labels": ["石叠 · 呼吸感", "晨雾 · 低饱和", "帘光 · 圆润"],
        "primary": ["#8AA89B", "#B8CDC2", "#5E7D70", "#F0F4F1", "#2F443B"],
        "secondary": ["#9A9288", "#C4BDB4", "#6B645C", "#FAF8F5", "#3B3733"],
        "btn_bg": "#5E7D70", "btn_color": "#FFFFFF",
        "input_border": "#B8CDC2",
        "card_border": "#E8EBE8",
        "tag_bg": "#F0F4F1", "tag_color": "#5E7D70",
        "toggle_bg": "#8AA89B",
    },
    {
        "id": "d",
        "name": "D 渊",
        "desc": "午夜蓝单色系加电光蓝强调。Linear 式冷调现代，锐利、快、有数据感，仪式感藏在动效里。",
        "keywords": ["冷调", "科技感", "锐利", "单色"],
        "font": "Inter",
        "img_keys": ["d1", "d2", "d3"],
        "img_labels": ["机身 · 冷光", "穹顶 · 午夜", "面板 · 电光蓝"],
        "primary": ["#5B9BFF", "#9CC3FF", "#2D5FB8", "#EAF2FF", "#12264D"],
        "secondary": ["#2A3548", "#55647D", "#1A2231", "#F5F8FD", "#0B111C"],
        "btn_bg": "#5B9BFF", "btn_color": "#0B111C",
        "input_border": "#2D5FB8",
        "card_border": "#26314A",
        "tag_bg": "#16233C", "tag_color": "#9CC3FF",
        "toggle_bg": "#5B9BFF",
    },
]

# ← EDIT: 产品信息
project_title = "天机"
project_slug = "tianji"
persona_line = "Persona: 18-35 岁泛娱乐用户，用玄学找乐子与心安"
jtbd_line = "JTBD: 输入生辰或问题，10 秒拿到确定的排盘与 AI 解读"

# ← EDIT: 组件文案（必须匹配产品语境）
BTN_PRIMARY = "开始排盘"
BTN_SECONDARY = "换一签"
INPUT_PLACEHOLDER = "1990-05-15 · 午时"
CARD_TITLE = "今日一言"
CARD_DESC = "宜静心，忌口舌 · 丙午年冬月十九"
TAG_A = "子平法"
TAG_B = "仅供娱乐"

# ─── 以下禁止修改 ───────────────────────────────────────────

LOCKED_CSS = """* { margin: 0; padding: 0; box-sizing: border-box; }
body {
  font-family: "PingFang SC", -apple-system, sans-serif;
  background: #FFFFFF;
  color: #1F2937;
  padding: 48px 40px;
  min-height: 100vh;
}
.container { max-width: 100%; margin: 0 auto; }
.scheme-nav {
  display: flex;
  gap: 0;
  margin-bottom: 48px;
  border-bottom: 1px solid #D1D5DB;
}
.scheme-tab {
  padding: 10px 24px;
  font-size: 13px;
  font-weight: 400;
  color: #6B7280;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}
.scheme-tab:hover { color: #1F2937; }
.scheme-tab.active {
  color: #1F2937;
  font-weight: 600;
  border-bottom-color: #1F2937;
}
.scheme-panel { display: none; }
.scheme-panel.active { display: block; }
.mood-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 40px;
}
.mood-img {
  height: 100px;
  display: flex;
  align-items: flex-end;
  padding: 8px;
}
.mood-img span {
  font-size: 9px;
  color: #fff;
  background: rgba(0,0,0,0.4);
  padding: 2px 6px;
}
.row-type {
  display: flex;
  align-items: center;
}
.type-display {
  flex: 0 0 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.type-display .aa {
  font-size: 120px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: -2px;
}
.type-display .font-name {
  font-size: 11px;
  color: #6B7280;
  margin-top: 10px;
}
.intro {
  flex: 1;
  padding-left: 40px;
}
.intro h2 {
  font-size: 15px;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 10px;
}
.intro p {
  font-size: 13px;
  color: #6B7280;
  line-height: 1.7;
  margin-bottom: 2px;
}
.intro .keywords {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}
.intro .kw {
  font-size: 10px;
  padding: 2px 8px;
  border: 1px solid #D1D5DB;
  color: #4B5563;
}
.divider {
  height: 1px;
  background: #D1D5DB;
  margin: 40px 0;
}
.row-colors {
  display: flex;
  gap: 40px;
}
.row-colors > div {
  flex: 1;
  min-width: 25%;
}
.color-group {
  display: flex;
  gap: 0;
  height: 160px;
  width: 100%;
}
.color-strip {
  height: 100%;
}
.color-module-title {
  font-size: 11px;
  font-weight: 600;
  color: #6B7280;
  margin-bottom: 10px;
  text-transform: uppercase;
  letter-spacing: 1px;
}
.row-comps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
}
.comp-col {
  padding: 0 15px;
  border-right: 1px solid #D1D5DB;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
.comp-col:first-child { padding-left: 0; }
.comp-col:last-child { border-right: none; padding-right: 0; }
.comp-col-title {
  font-size: 10px;
  color: #6B7280;
  text-transform: uppercase;
  letter-spacing: 0.8px;
  margin-bottom: 4px;
}
.btn {
  display: inline-block;
  padding: 6px 16px;
  font-size: 11px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  text-align: center;
  white-space: nowrap;
}
.input-demo {
  padding: 6px 10px;
  border: 1px solid #D1D5DB;
  font-size: 11px;
  background: #F9FAFB;
  width: 120px;
  color: #6B7280;
}
.card-demo {
  border: 1px solid #D1D5DB;
  padding: 10px;
  max-width: 140px;
}
.card-demo .card-title {
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 3px;
}
.card-demo .card-desc {
  font-size: 10px;
  color: #6B7280;
}
.tag {
  display: inline-block;
  padding: 2px 6px;
  font-size: 9px;
  font-weight: 500;
}
.toggle {
  width: 30px; height: 16px;
  border-radius: 8px;
  position: relative;
}
.toggle::after {
  content: '';
  position: absolute;
  width: 12px; height: 12px;
  background: #fff;
  border-radius: 50%;
  top: 2px; right: 2px;
}
.header {
  margin-bottom: 32px;
}
.header h1 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 6px;
}
.header p {
  font-size: 12px;
  color: #6B7280;
}"""


def render_scheme(s, active=False):
    active_cls = " active" if active else ""
    mood_html = ""
    for key, label in zip(s["img_keys"], s["img_labels"]):
        mood_html += (
            f'<div class="mood-img" style="background:url(data:image/jpeg;base64,'
            f'{b64[key]}) center/cover no-repeat;"><span>{label}</span></div>\n'
        )
    mood_html += (
        f'<div class="mood-img" style="background:linear-gradient(135deg, '
        f'{s["primary"][0]}, {s["secondary"][0]});"><span>色彩延伸</span></div>\n'
    )
    flexes = [8, 4, 3, 2, 2]
    pri = "".join(f'<div class="color-strip" style="flex:{fx};background:{c};"></div>' for c, fx in zip(s["primary"], flexes))
    sec = "".join(f'<div class="color-strip" style="flex:{fx};background:{c};"></div>' for c, fx in zip(s["secondary"], flexes))
    kw = "".join(f'<span class="kw">{k}</span>' for k in s["keywords"])

    return f'''<div class="scheme-panel{active_cls}" id="panel-{s['id']}">
  <div class="mood-row">{mood_html}</div>
  <div class="row-type">
    <div class="type-display">
      <div class="aa" style="font-family:'{s['font']}',sans-serif;">Aa</div>
      <div class="font-name">{s['font']}</div>
    </div>
    <div class="intro">
      <h2>{s['name']}</h2>
      <p>{s['desc']}</p>
      <div class="keywords">{kw}</div>
    </div>
  </div>
  <div class="divider"></div>
  <div class="row-colors">
    <div><div class="color-module-title">Primary</div><div class="color-group">{pri}</div></div>
    <div><div class="color-module-title">Secondary</div><div class="color-group">{sec}</div></div>
  </div>
  <div class="divider"></div>
  <div class="row-comps">
    <div class="comp-col">
      <div class="comp-col-title">Buttons</div>
      <button class="btn" style="background:{s['btn_bg']};color:{s['btn_color']};">{BTN_PRIMARY}</button>
      <button class="btn" style="background:transparent;color:{s['btn_bg']};border:1px solid {s['btn_bg']};">{BTN_SECONDARY}</button>
    </div>
    <div class="comp-col">
      <div class="comp-col-title">Input</div>
      <input class="input-demo" style="border-color:{s['input_border']};" value="{INPUT_PLACEHOLDER}" readonly>
    </div>
    <div class="comp-col">
      <div class="comp-col-title">Card</div>
      <div class="card-demo" style="border-color:{s['card_border']};">
        <div class="card-title">{CARD_TITLE}</div>
        <div class="card-desc">{CARD_DESC}</div>
      </div>
    </div>
    <div class="comp-col">
      <div class="comp-col-title">Tags</div>
      <span class="tag" style="background:{s['tag_bg']};color:{s['tag_color']};">{TAG_A}</span>
      <span class="tag" style="background:{s['tag_bg']};color:{s['tag_color']};">{TAG_B}</span>
      <div class="toggle" style="background:{s['toggle_bg']};"></div>
    </div>
  </div>
</div>'''


tabs_html = ""
for i, s in enumerate(schemes):
    ac = " active" if i == 0 else ""
    tabs_html += f'<div class="scheme-tab{ac}" onclick="switchTab(\'{s["id"]}\')">{s["name"]}</div>'

panels_html = ""
for i, s in enumerate(schemes):
    panels_html += render_scheme(s, active=(i == 0))

html = f'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{project_title} — Design System Mood Board</title>
<style>
{LOCKED_CSS}
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <h1>{project_title} &mdash; Design System Mood Board</h1>
    <p>{persona_line} &middot; {jtbd_line}</p>
  </div>
  <div class="scheme-nav">{tabs_html}</div>
  {panels_html}
</div>
<script>
function switchTab(id) {{
  document.querySelectorAll('.scheme-tab').forEach(function(t) {{ t.classList.remove('active'); }});
  document.querySelectorAll('.scheme-panel').forEach(function(p) {{ p.classList.remove('active'); }});
  event.currentTarget.classList.add('active');
  document.getElementById('panel-' + id).classList.add('active');
}}
</script>
</body>
</html>'''

import os
os.makedirs("spark-output/board", exist_ok=True)
board_path = f"spark-output/board/{project_slug}-moodboard.html"
with open(board_path, "w", encoding="utf-8") as f:
    f.write(html)
print(f"Done: {os.path.getsize(board_path)/1024:.0f} KB")
